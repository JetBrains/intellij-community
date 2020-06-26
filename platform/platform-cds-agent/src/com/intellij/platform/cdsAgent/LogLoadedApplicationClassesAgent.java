// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.cdsAgent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class is executed as -javaagent, it must not
 * have any runtime dependencies, it cannot be written
 * in Kotlin (to avoid dealing with kotlin-stdlib*.jar or
 * other dependencies)
 * <p>
 * Java Agent to log all loaded class in CDS supported format
 * [JEP 310](https://openjdk.java.net/jeps/310)
 */
public final class LogLoadedApplicationClassesAgent {
  public static final String TARGET_FILE = "JB_CDS_TARGET_FILE";
  public static final String USE_APP_CDS = "JB_CDS_USE_APP_CDS";

  public static void premain(String agentArgs, Instrumentation inst) {
    try {
      premainImpl(agentArgs, inst);
    }
    catch (Throwable t) {
      log("Failed to setup LogLoadedApplicationClassesAgent agent", t);
    }
  }

  public static void agentmain(String agentArgs, Instrumentation inst) {
    try {
      log("Injecting LogLoadedApplicationClassesAgent: " + agentArgs);

      TransitiveClassesCollector myAllClasses = new TransitiveClassesCollector();
      myAllClasses.addClasses(inst);
      logDetectedClasses(true, new File(agentArgs), myAllClasses.getAllClasses());

      log("LogLoadedApplicationClassesAgent completed");
    }
    catch (Throwable t) {
      log("Failed to setup LogLoadedApplicationClassesAgent agent", t);
    }
  }

  private static void premainImpl(String agentArgs, Instrumentation inst) {
    log("");
    log("Starting LogLoadedApplicationClassesAgent: " + agentArgs);
    log("");

    String targetFileName = System.getenv().get(TARGET_FILE);
    boolean useAppCDS = Boolean.valueOf(System.getenv().getOrDefault(USE_APP_CDS, "true"));

    if (targetFileName == null) {
      log("Failed to find " + TARGET_FILE + " parameter to -javaagentpath");
      return;
    }

    File targetFile = new File(targetFileName);
    //noinspection ResultOfMethodCallIgnored
    targetFile.getParentFile().mkdirs();

    final TransitiveClassesCollector myAllClasses = new TransitiveClassesCollector();

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r, "LogLoadedApplicationClassesAgent-watcher");
        thread.setDaemon(true);
        return thread;
      }
    });

    executor.scheduleWithFixedDelay(() -> myAllClasses.addClasses(inst), 0, 20, TimeUnit.MILLISECONDS);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log("Shutdown is started...");
      executor.shutdown();
      try {
        executor.awaitTermination(2, TimeUnit.SECONDS);
      }
      catch (InterruptedException e) {
        log("Failed to wait for classes dump thread termination", e);
      }

      logDetectedClasses(useAppCDS, targetFile, myAllClasses.getAllClasses());
    }, "LogLoadedApplicationClassesAgent-shutdown"));

    log("LogLoadedApplicationClassesAgent was configured");
  }

  private static class TransitiveClassesCollector {
    final Set<Class<?>> allMyClasses = new HashSet<>();

    public void addClasses(@NotNull Instrumentation inst) {
      try {
        addClasses(inst.getAllLoadedClasses());
      }
      catch (Throwable t) {
        log("Failed to collect all classes from Instrumentation", t);
      }
    }

    private void addClasses(@NotNull Class<?>[] classes) {
      Queue<Class<?>> queue = new ArrayDeque<>();
      Collections.addAll(queue, classes);

      while (true) {
        Class<?> next = queue.poll();
        if (next == null) break;
        String className = next.getName();

        try {
          //skip array classes
          if (next.isArray()) continue;
          if (className.contains("$$Lambda$")) continue;
          if (className.startsWith("java.lang.invoke.LambdaForm$")) continue;
          if (className.startsWith("jdk.internal.reflect.Generated")) continue;
          if (className.startsWith("com.sun.proxy.$Proxy")) continue;
          if (className.contains(".$Proxy")) continue;
          if (className.startsWith("java.lang.invoke.BoundMethodHandle$")) continue;

          if (!allMyClasses.add(next)) continue;

          Class<?> superclass = next.getSuperclass();
          if (superclass != null) queue.add(superclass);
          Collections.addAll(queue, next.getInterfaces());
        }
        catch (Throwable t) {
          log("Failed to process " + className, t);
        }
      }
    }

    @NotNull
    public Collection<Class<?>> getAllClasses() {
      return allMyClasses;
    }
  }

  private static void logDetectedClasses(boolean useAppCDS,
                                         File targetFile,
                                         Collection<Class<?>> allClasses) {
    final Path basePath;
    try {
      basePath = new File(".").getCanonicalFile().toPath();
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to resolve current working directory path", e);
    }

    ClassesLogger logger = new ClassesLogger(basePath, useAppCDS);
    //first output classes without getProtectionDomain().getCodeSource().getLocation, aka likely JDK classes

    Set<Class<?>> withoutSource = new HashSet<>();
    Set<Class<?>> libSource = new HashSet<>();
    Set<Class<?>> pluginsSource = new HashSet<>();

    allClasses.stream().sorted(Comparator.comparing(clazz -> clazz.getName())).forEach(clazz -> {
      String source = getSourceForClassesList(basePath, clazz);
      if (source == null) {
        withoutSource.add(clazz);
      }
      else if (!source.contains("plugins/")) {
        libSource.add(clazz);
      }
      else {
        pluginsSource.add(clazz);
      }
    });

    logger.addComment("System CDS block " + withoutSource.size() + " classes");

    ///we will see that class too often in the list, let's make it have and ID=1
    logger.logClass(Object.class);

    logger.attachClasses(withoutSource);

    if (useAppCDS) {
      logger.addComment("\n\nApplication CDS block, " + (libSource.size() + pluginsSource.size()) + " classes");
      logger.attachClasses(libSource);
      logger.attachClasses(pluginsSource);
    }
    else {
      logger.addComment("\n\nApplication CDS is DISABLED, skipping " + libSource.size() + pluginsSource.size() + " classes ");
    }

    try {
      logger.writeClassesList(targetFile);
    }
    catch (Throwable e) {
      log("Failed to write report file to " + targetFile, e);
    }

    log("===================================================");
    log("Created classes list file to " + targetFile);
    log("System classes: " + withoutSource.size());
    log("Application classes: " + (libSource.size() + pluginsSource.size()));
    log("AppCDS is " + (useAppCDS ? "enabled" : "DISABLED"));
    log("===================================================");
  }

  private static final class ClassInfo {
    private final int id;
    private final Class<?> clazz;
    private final String source;
    private final String name;

    private ClassInfo superClass = null;
    private List<ClassInfo> interfaces = null;

    private ClassVersionAssertOutcome assertClassVersion;
    private Boolean hasSameNamedClasses = null;
    private Boolean isValid = null;
    private boolean isLogged = false;

    private List<String> myIsNotValidReasons = null;
    private Integer myTooOldClassVersion = null;

    private ClassInfo(int id, @NotNull Class<?> clazz, @Nullable String source) {
      this.id = id;
      this.clazz = clazz;
      this.source = source;
      this.name = getVMClassName(clazz);
    }

    @NotNull
    String toLine() {
      final StringBuilder lineBuilder = new StringBuilder();

      lineBuilder.append(name).append(" id: ").append(id);
      if (source != null) {
        lineBuilder.append(" super: ").append(superClass.id);

        if (!interfaces.isEmpty()) {
          lineBuilder.append(" interfaces:");
          for (ClassInfo anInterface : interfaces) {
            lineBuilder.append(" ").append(anInterface.id);
          }
        }

        lineBuilder.append(" source: ").append(source);
      }

      return lineBuilder.toString();
    }
  }

  /**
   * Generated report file in the classes list format
   * compatible with JDK loader from src/hotspot/share/classfile/classListParser.cpp
   */
  private static final class ClassesLogger {
    private int myIdCounter = 0;
    private final Map<Class<?>, ClassInfo> myClasses = new LinkedHashMap<>();
    private final List<String> myLog = new ArrayList<>();

    private final Path basePath;
    private final boolean useAppCDS;

    private ClassesLogger(Path basePath, boolean useAppCDS) {
      this.basePath = basePath;
      this.useAppCDS = useAppCDS;
    }

    public void addComment(@NotNull String comment) {
      for (String line : comment.split("\n")) {
        myLog.add("### " + line);
      }
    }

    @NotNull
    private ClassInfo logClass(@NotNull Class<?> clazz) {
      try {
        String source = getSourceForClassesList(basePath, clazz);

        ClassInfo info = myClasses.get(clazz);
        if (info != null) {
          return info;
        }

        info = new ClassInfo(++myIdCounter, clazz, source);
        myClasses.put(clazz, info);

        //resolve super class
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null) superclass = Object.class;
        info.superClass = logClass(superclass);

        //resolve interfaces
        List<ClassInfo> interfaces = new ArrayList<>();
        for (Class<?> aClass : clazz.getInterfaces()) {
          interfaces.add(logClass(aClass));
        }
        info.interfaces = interfaces;
        return info;
      }
      catch (Throwable t) {
        throw new RuntimeException("Failed to process class " + clazz.getName(), t);
      }
    }

    public void attachClasses(Collection<Class<?>> classes) {
      classes.forEach(this::logClass);

      Map<String, List<ClassInfo>> classesByNames = myClasses.values().stream().collect(Collectors.groupingBy(info -> info.name));
      for (Map.Entry<String, List<ClassInfo>> entry : classesByNames.entrySet()) {
        // we may discover a class that is not in classes and was not yet added via attachClasses
        if (entry.getValue().size() <= 1) {
          entry.getValue().forEach(info -> info.hasSameNamedClasses = false);
          continue;
        }

        // we have same-named class from several class loaders
        // It is only allowed (see ClassListParser::load_class_from_source function)
        // to have one class without source and one class with source.
        //
        // we check if there were classes from the previous call to #attachClasses
        // next we try to leave class without source, or any other class

        ClassInfo classWithSource = null;
        ClassInfo classWithoutSource = null;

        //check decided classes for results
        for (ClassInfo info : entry.getValue()) {
          if (info.hasSameNamedClasses == null) continue;

          if (!Boolean.TRUE.equals(info.hasSameNamedClasses)) {
            if (info.source == null) {
              classWithoutSource = info;
            }
            else {
              classWithSource = info;
            }
          }
        }

        //check undecided classes and update
        for (ClassInfo info : entry.getValue()) {
          if (info.hasSameNamedClasses != null) continue;

          if (classWithoutSource == null && info.source == null) {
            classWithoutSource = info;
            info.hasSameNamedClasses = false;
            continue;
          }

          if (classWithSource == null && info.source != null) {
            classWithSource = info;
            info.hasSameNamedClasses = false;
            continue;
          }

          info.hasSameNamedClasses = true;
        }
      }

      //compute isValid predicate explicitly (it has cache)
      myClasses.values().forEach(this::isValid);

      myClasses.values().stream()
        .sorted(Comparator.comparing(info -> info.name))
        .forEach(this::logLine);
    }

    private boolean isValid(@NotNull ClassInfo info) {
      if (info.isValid != null) {
        return info.isValid;
      }

      if (info.clazz.getClassLoader() == null || info.clazz.getClassLoader() == ClassLoader.getSystemClassLoader()) {
        info.isValid = true;
        return true;
      }

      List<String> isNotValidReasons = new ArrayList<>();
      if (!useAppCDS && info.source != null) {
        isNotValidReasons.add("the class has non-system source with disabled AppCDS");
      }

      if (info.source != null) {
        if (info.source.contains(" ")) {
          isNotValidReasons.add("the class has whitespace in path, which is not supported by the JVM");
        }

        if (!info.source.endsWith(".jar")) {
          isNotValidReasons.add("only .jar files are supported by CDS");
        }
      }

      info.assertClassVersion = assertClassVersion(info);
      switch (info.assertClassVersion) {
        case ERROR:
          isNotValidReasons.add("class version assert failed");
          break;
        case NOT_FOUND:
          isNotValidReasons.add(".class is not found in the resources");
          break;
        case TOO_OLD:
          isNotValidReasons.add("class version is too old: " + info.myTooOldClassVersion);
          break;
        case OK:
          break;
        default:
          throw new RuntimeException("Unknown case " + info.myTooOldClassVersion + " for " + info.name);
      }

      if (Boolean.TRUE.equals(info.hasSameNamedClasses)) {
        isNotValidReasons.add("same named class already exists");
      }

      if (!isValid(info.superClass)) {
        isNotValidReasons.add("invalid superclass " + info.superClass.name);
      }

      for (ClassInfo anInterface : info.interfaces) {
        if (!isValid(anInterface)) {
          isNotValidReasons.add("invalid interface " + anInterface.name);
        }
      }

      if (!isNotValidReasons.isEmpty()) {
        info.myIsNotValidReasons = isNotValidReasons;
      }

      info.isValid = isNotValidReasons.isEmpty();
      return info.isValid;
    }

    private void logLine(@NotNull ClassInfo info) {
      if (info.isLogged) return;
      info.isLogged = true;

      if (!isValid(info)) return;
      logLine(info.superClass);

      for (ClassInfo anInterface : info.interfaces) {
        logLine(anInterface);
      }

      myLog.add(info.toLine());
    }

    @NotNull
    public List<String> generateReportHeader() {
      List<String> lines = new ArrayList<>();
      lines.add("       use AppCDS: " + useAppCDS);
      lines.add("    Total classes: " + myClasses.size());
      lines.add("   system classes: " + myClasses.values().stream().filter(info -> info.source == null).count());
      lines.add("  pre 1.6 classes: " + myClasses.values().stream().filter(info -> info.myTooOldClassVersion != null).count());
      lines.add("  ignored classes: " + myClasses.values().stream().filter(info -> !isValid(info)).count());
      lines.add("");
      return lines;
    }

    @NotNull
    public List<String> generateReportWarnings() {
      List<ClassInfo> allInfos = myClasses.values().stream().sorted(Comparator.comparing(info -> info.name)).collect(Collectors.toList());

      List<String> lines = new ArrayList<>();
      lines.add("Pre 1.6 classes:");
      for (ClassInfo item : allInfos) {
        if (item.myTooOldClassVersion != null) {
          lines.add("  " + item.name + " has version " + item.myTooOldClassVersion);
        }
      }

      lines.add("Same classes from different JARs:");
      Map<String, List<ClassInfo>> groupByName =
        allInfos.stream().collect(Collectors.groupingBy(info -> info.name, TreeMap::new, Collectors.toList()));
      for (Map.Entry<String, List<ClassInfo>> entry : groupByName.entrySet()) {
        if (entry.getValue().size() <= 1) continue;
        lines.add("  " + entry.getKey());
        for (ClassInfo source : entry.getValue()) {
          lines.add("    " + source.source);
        }
      }

      lines.add("Transitively invalid classes:");
      for (ClassInfo entry : allInfos) {
        if (entry.myIsNotValidReasons == null) continue;
        lines.add("  " + entry.name);
        for (String warning : entry.myIsNotValidReasons) {
          lines.add("    " + warning);
        }
      }

      return lines;
    }

    public void writeClassesList(@NotNull File file) throws IOException {
      List<String> lines = new ArrayList<>();
      lines.add("### Classes List for IntelliJ based IDE");
      for (String line : generateReportHeader()) {
        lines.add("### " + line);
      }
      lines.add("###");
      lines.add("### see detailed report at the end of the file");
      lines.add("###");
      lines.addAll(myLog);
      lines.add("#################################################### ");
      lines.add("### ");
      lines.add("### Warnings report:");
      for (String rant : generateReportWarnings()) {
        lines.add("### " + rant);
      }

      Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }
  }

  private enum ClassVersionAssertOutcome {OK, TOO_OLD, NOT_FOUND, ERROR}

  private static ClassVersionAssertOutcome assertClassVersion(ClassInfo info) {
    ClassLoader classLoader = info.clazz.getClassLoader();
    if (classLoader == null) classLoader = ClassLoader.getSystemClassLoader();

    //NOTE: also include the Java 9+ locations under META-INF
    String resourceName = info.name + ".class";
    try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
      if (is == null) {
        return ClassVersionAssertOutcome.NOT_FOUND;
      }

      try (DataInputStream dis = new DataInputStream(is)) {
        if (dis.readInt() != 0xcafebabe) {
          return ClassVersionAssertOutcome.ERROR;
        }

        /*int minor = */
        dis.readUnsignedShort();
        int major = dis.readUnsignedShort();

        if (major <= 49) {
          info.myTooOldClassVersion = major;
          /*
             Java 1.4  48
             Java 5    49
             Java 6    50
             Java 7    51
             Java 8    52
             Java 9    53
           */
          return ClassVersionAssertOutcome.TOO_OLD;
        }

        return ClassVersionAssertOutcome.OK;
      }
    }
    catch (Throwable e) {
      log("Failed to read bytes of " + info.name, e);
      return ClassVersionAssertOutcome.ERROR;
    }
  }

  @Nullable
  private static String getSourceForClassesList(@Nullable Path basePath, @NotNull Class<?> clazz) {
    final ClassLoader loader = clazz.getClassLoader();
    if (loader == null) return null;
    if (loader == ClassLoader.getSystemClassLoader()) return null;

    String resourceName = getClassResourcePath(clazz);
    URL url = loader.getResource(resourceName);
    if (url == null) return null;

    String path = url.toString();

    if (path.startsWith("jar:")) {
      int sep = path.lastIndexOf("!/");
      path = path.substring(4, sep);
    }

    if (path.startsWith("jrt:")) {
      //platform classes goes without source: attribute
      return null;
    }

    if (!path.startsWith("file:")) {
      log("Class " + clazz.getName() + " has unexpected source: " + url);
      return path;
    }

    if (basePath != null) {
      try {
        Path resolvedPath = Paths.get(URI.create(path));
        try {
          path = basePath.relativize(resolvedPath).toString();
        }
        catch (Throwable t) {
          //can fail because of a file-system, e.g. java.lang.IllegalArgumentException: 'other' has different root
          path = resolvedPath.toString();
        }
      }
      catch (Throwable t) {
        //MOP
      }
    }

    return path;
  }

  @NotNull
  private static String getVMClassName(@NotNull Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  @NotNull
  private static String getClassResourcePath(@NotNull Class<?> clazz) {
    //NOTE: consider Java 9+ locations under META-INF
    return getVMClassName(clazz) + ".class";
  }

  private static void log(String message) {
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println("[CDS-AGENT] " + message);
  }

  private static void log(String message, @Nullable Throwable t) {
    log(message + ". " + t);
    if (t != null) {
      //noinspection CallToPrintStackTrace
      t.printStackTrace();
    }
  }
}

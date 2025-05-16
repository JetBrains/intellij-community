// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.incremental.LineOutputWriter;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.jps.util.Iterators.BooleanFunction;
import org.jetbrains.jps.util.Iterators.Function;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@ApiStatus.Internal
public final class JavacMain {
  //private static final boolean ECLIPSE_COMPILER_SINGLE_THREADED_MODE = Boolean.parseBoolean(System.getProperty("jdt.compiler.useSingleThread", "false"));
  private static final Set<String> FILTERED_OPTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    "-d", "-classpath", "-cp", "--class-path", "-bootclasspath", "--boot-class-path"
  )));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    /*javac options*/  "-verbose", "-implicit:class", "-implicit:none", "-Xprefer:newer", "-Xprefer:source"
  )));
  private static final Set<String> FILE_MANAGER_EARLY_INIT_OPTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    "-encoding", "-extdirs", "-endorseddirs", "-processorpath", "--processor-path", "--processor-module-path", "-s", "-d", "-h"
  )));

  public static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version");
  private static final boolean JAVA_RUNTIME_PRE_9 =
    JAVA_RUNTIME_VERSION.startsWith("1.8.") ||
    JAVA_RUNTIME_VERSION.startsWith("8.")   ||
    JAVA_RUNTIME_VERSION.startsWith("1.7.") ||
    JAVA_RUNTIME_VERSION.startsWith("7.")   ||
    JAVA_RUNTIME_VERSION.startsWith("1.6.") ||
    JAVA_RUNTIME_VERSION.startsWith("6.");

  public static final String TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY = "jps.track.ap.dependencies";
  public static final boolean TRACK_AP_GENERATED_DEPENDENCIES = Boolean.parseBoolean(System.getProperty(TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY, "true"));

  public static boolean compile(Iterable<String> options,
                                Iterable<? extends File> sources,
                                Iterable<? extends File> classpath,
                                Iterable<? extends File> platformClasspath,
                                ModulePath modulePath,
                                Iterable<? extends File> upgradeModulePath,
                                Iterable<? extends File> sourcePath,
                                final Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer diagnosticConsumer,
                                final OutputFileConsumer outputSink,
                                CanceledStatus canceledStatus,
                                @NotNull JavaCompilingTool compilingTool,
                                @Nullable InputFileDataProvider inputFileDataProvider) {
    return compile(options,
      sources,
      classpath,
      platformClasspath,
      modulePath,
      upgradeModulePath,
      sourcePath,
      outputDirToRoots,
      diagnosticConsumer,
      outputSink,
      canceledStatus,
      compilingTool,
      null, inputFileDataProvider);
  }

  public static boolean compile(Iterable<String> options,  // todo: to remove
                                Iterable<? extends File> sources,
                                Iterable<? extends File> classpath,
                                Iterable<? extends File> platformClasspath,
                                ModulePath modulePath,
                                Iterable<? extends File> upgradeModulePath,
                                Iterable<? extends File> sourcePath,
                                final Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer diagnosticConsumer,
                                final OutputFileConsumer outputSink,
                                CanceledStatus canceledStatus,
                                @NotNull JavaCompilingTool compilingTool,
                                @Nullable JpsJavacFileProvider jpsJavacFileProvider) {
    return compile(options,
      sources,
      classpath,
      platformClasspath,
      modulePath,
      upgradeModulePath,
      sourcePath,
      outputDirToRoots,
      diagnosticConsumer,
      outputSink,
      canceledStatus,
      compilingTool,
      jpsJavacFileProvider, null);
  }

  private static boolean compile(Iterable<String> options,
                                Iterable<? extends File> sources,
                                Iterable<? extends File> classpath,
                                Iterable<? extends File> platformClasspath,
                                ModulePath modulePath,
                                Iterable<? extends File> upgradeModulePath,
                                Iterable<? extends File> sourcePath,
                                final Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer diagnosticConsumer,
                                final OutputFileConsumer outputSink,
                                CanceledStatus canceledStatus,
                                @NotNull JavaCompilingTool compilingTool,
                                @Nullable JpsJavacFileProvider jpsJavacFileProvider,
                                @Nullable InputFileDataProvider inputFileDataProvider) {
    JavaCompiler compiler;
    try {
      compiler = compilingTool.createCompiler();
    }
    catch (CannotCreateJavaCompilerException e) {
      diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
      return false;
    }

    for (File outputDir : outputDirToRoots.keySet()) {
      outputDir.mkdirs();
    }

    final boolean usingJavac = compilingTool instanceof JavacCompilerTool;
    final boolean javacBefore9 = usingJavac && JAVA_RUNTIME_PRE_9; // since java 9 internal API's used by the optimizedFileManager have changed
    final JpsJavacFileManager fileManager = new JpsJavacFileManager(
      new ContextImpl(compiler, diagnosticConsumer, outputSink, modulePath, canceledStatus), javacBefore9, JavaSourceTransformer.getTransformers(),
      jpsJavacFileProvider, inputFileDataProvider
    );
    if (javacBefore9 && !Iterators.isEmpty(platformClasspath)) {
      // for javac6 this will prevent lazy initialization of Paths.bootClassPathRtJar
      // and thus usage of symbol file for resolution, when this file is not expected to be used
      fileManager.handleOption("-bootclasspath", Collections.singleton("").iterator());
      fileManager.handleOption("-extdirs", Collections.singleton("").iterator()); // this will clear cached stuff
      fileManager.handleOption("-endorseddirs", Collections.singleton("").iterator()); // this will clear cached stuff
    }

    final Iterable<String> _options = prepareOptions(options, compilingTool);

    try {
      // to be on the safe side, we'll have to apply all options _before_ calling any of manager's methods
      // i.e. getJavaFileObjectsFromFiles()
      // This way the manager will be properly initialized. Namely, the encoding will be set correctly
      // Note that due to lazy initialization in various components inside javac, handleOption() should be called before setLocation() and others
      // update: for some options their repetitive initialization would be considered as error: e.g. '--patch-module',
      //  therefore we do the trick only for those options that may influence FileManager's state initialization before passing it to getTask() method
      for (Iterator<String> iterator = _options.iterator(); iterator.hasNext(); ) {
        final String option = iterator.next();
        if (FILE_MANAGER_EARLY_INIT_OPTIONS.contains(option)) {
          fileManager.handleOption(option, iterator);
        }
      }

      try {
        fileManager.setOutputDirectories(outputDirToRoots);
      }
      catch (IOException e) {
        fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
        return false;
      }

      if (!Iterators.isEmpty(platformClasspath)) {
        try {
          fileManager.handleOption("-bootclasspath", Collections.singleton("").iterator()); // this will clear cached stuff
          fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, buildPlatformClasspath(platformClasspath, _options));
        }
        catch (IOException e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
          return false;
        }
      }

      if (!Iterators.isEmpty(upgradeModulePath)) {
        try {
          setLocation(fileManager, "UPGRADE_MODULE_PATH", upgradeModulePath);
        }
        catch (IOException e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
          return false;
        }
      }

      final boolean isAnnotationProcessingEnabled = isAnnotationProcessingEnabled(_options);

      if (!modulePath.isEmpty()) {
        try {
          setLocation(fileManager, "MODULE_PATH", modulePath.getPath());
          if (isAnnotationProcessingEnabled &&
            getLocation(fileManager, "ANNOTATION_PROCESSOR_MODULE_PATH") == null &&
            fileManager.getLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH) == null) {
            // default annotation processing discovery path to module path if not explicitly set
            setLocation(fileManager, "ANNOTATION_PROCESSOR_MODULE_PATH", Iterators.filter(modulePath.getPath(), new BooleanFunction<File>() {
              @Override
              public boolean fun(File file) {
                return !outputDirToRoots.containsKey(file);
              }
            }));
          }
        }
        catch (IOException e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
          return false;
        }
      }

      if (modulePath.isEmpty() || !Iterators.isEmpty(classpath)) {
        // because module path has priority if present, initialize classpath after the module path
        try {
          fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
          if (!usingJavac &&
              isAnnotationProcessingEnabled &&
              !Iterators.contains(_options, "-processorpath") &&
              !Iterators.contains(_options, "--processor-module-path") &&
              getLocation(fileManager, "ANNOTATION_PROCESSOR_MODULE_PATH") == null) {
            // for non-javac file manager ensure annotation processor path defaults to classpath
            fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, classpath);
          }
        }
        catch (IOException e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
          return false;
        }
      }

      if (javacBefore9 || !Iterators.isEmpty(sourcePath) || modulePath.isEmpty()) {
        try {
          // ensure the source path is set;
          // otherwise, if not set, javac attempts to search both classes and sources in classpath;
          // so if some classpath jars contain sources, it will attempt to compile them
          // starting from javac9 it seems that setting empty source path may affect module compilation logic, so starting from javac9
          // we avoid forcing empty sourcepath
          fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePath);
        }
        catch (IOException e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
          return false;
        }
      }

      final APIWrappers.ProcessingContext procContext;
      final DiagnosticOutputConsumer diagnosticListener;
      if (TRACK_AP_GENERATED_DEPENDENCIES && isAnnotationProcessingEnabled) {
        procContext = new APIWrappers.ProcessingContext(fileManager);
        // use real processor class names and not names of processor wrappers
        diagnosticListener = APIWrappers.newDiagnosticListenerWrapper(procContext, diagnosticConsumer);
      }
      else {
        procContext = null;
        diagnosticListener = diagnosticConsumer;
      }

      final LineOutputWriter out = new LineOutputWriter() {
        @Override
        protected void lineAvailable(String line) {
          if (usingJavac) {
            diagnosticListener.outputLineAvailable(line);
          }
          else {
            // todo: filter too verbose eclipse output?
          }
        }
      };

      // methods added to newer versions of StandardJavaFileManager interfaces have default implementations that
      // do not delegate to corresponding methods of FileManager's base implementation
      // this proxy object makes sure the calls, not implemented in our file manager, are dispatched further to the base file manager implementation
      final StandardJavaFileManager fm = APIWrappers.wrap(StandardJavaFileManager.class, fileManager, fileManager.getClass().getSuperclass(), fileManager.getStdManager());
      final JavaCompiler.CompilationTask task = tryInstallClientCodeWrapperCallDispatcher(compiler.getTask(
        out, fm, diagnosticListener, _options, null, fileManager.setInputSources(sources)
      ), fm);

      for (JavaCompilerToolExtension extension : JavaCompilerToolExtension.getExtensions()) {
        try {
          extension.beforeCompileTaskExecution(compilingTool, task, _options, diagnosticConsumer);
        }
        catch (Throwable e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.MANDATORY_WARNING, extension.getClass() + " : " + e.getMessage());
          e.printStackTrace(System.err);
        }
      }

      if (TRACK_AP_GENERATED_DEPENDENCIES && isAnnotationProcessingEnabled) {
        final Iterable<Processor> processors = lookupAnnotationProcessors(procContext, getAnnotationProcessorNames(_options));
        if (processors != null) {
          task.setProcessors(processors);
        }
      }

      return task.call();
    }
    catch(IllegalArgumentException | IllegalStateException e) {
      diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
    }
    catch (CompilationCanceledException e) {
      diagnosticConsumer.report(new JpsInfoDiagnostic(e.getMessage()));
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause != null) {
        if (cause instanceof CompilationCanceledException) {
          diagnosticConsumer.report(new JpsInfoDiagnostic(cause.getMessage()));
        }
        else {
          diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, buildCompilerErrorMessage(e)));
          throw e;
        }
      }
      else {
        throw e;
      }
    }
    finally {
      fileManager.close();
      if (usingJavac) {
        cleanupJavacNameTable();
      }
    }
    return false;
  }

  @Nullable
  private static Iterable<Processor> lookupAnnotationProcessors(final APIWrappers.ProcessingContext procContext, @Nullable Iterable<String> processorNames) {
    try {
      Iterable<Processor> processors = null;

      if (hasLocation(procContext.getFileManager(), "ANNOTATION_PROCESSOR_MODULE_PATH")) {
        // this is equivalent to
        //processors = fileManager.getServiceLoader(StandardLocation.locationFor("ANNOTATION_PROCESSOR_MODULE_PATH"), Processor.class);
        // if java modules are involved, they should be properly handled by the fileManager

        //noinspection unchecked
        processors = (ServiceLoader<Processor>)JavaFileManager.class.getMethod("getServiceLoader", JavaFileManager.Location.class, Class.class).invoke(
          procContext.getFileManager(), StandardLocation.locationFor("ANNOTATION_PROCESSOR_MODULE_PATH"), Processor.class
        );
        if (processorNames != null) {
          processors = Iterators.filterWithOrder(processors, Iterators.map(processorNames, new Function<String, BooleanFunction<? super Processor>>() {
            @Override
            public BooleanFunction<? super Processor> fun(final String procName) {
              return new BooleanFunction<Processor>() {
                @Override
                public boolean fun(Processor processor) {
                  return procName.equals(processor.getClass().getName());
                }
              };
            }
          }));
        }
      }
      else {
        final ClassLoader processorClassLoader = procContext.getFileManager().getClassLoader(
          // If processorpath is not explicitly set, use the classpath.
          procContext.getFileManager().hasLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH) ? StandardLocation.ANNOTATION_PROCESSOR_PATH : StandardLocation.CLASS_PATH
        );
        if (processorClassLoader != null) {
          if (processorNames != null) {
            final List<Processor> loaded = new ArrayList<>();
            for (String procName : processorNames) {
              loaded.add((Processor)processorClassLoader.loadClass(procName).getDeclaredConstructor().newInstance());
            }
            processors = loaded;
          }
          else {
            processors = ServiceLoader.load(Processor.class, processorClassLoader);
          }
        }
      }

      if (processors != null) {
        return procContext.wrapProcessors(processors);
      }
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  @Nullable
  private static Iterable<String> getAnnotationProcessorNames(Iterable<String> options) {
    for (Iterator<String> it = options.iterator(); it.hasNext(); ) {
      final String option = it.next();
      if ("-processor".equals(option)) {
        return it.hasNext()? Arrays.asList(it.next().split(",")) : null;
      }
    }
    return null;
  }

  @Nls
  private static String buildCompilerErrorMessage(Throwable e) {
    return new Object() {
      @Nls
      final StringBuilder buf = new StringBuilder();
      @Nls
      String collectAllMessages(Throwable e, Set<Throwable> processed) {
        if (e != null && processed.add(e)) {
          final String msg = e.getMessage();
          if (msg != null && !msg.trim().isEmpty() && buf.indexOf(msg) < 0) {
            if (buf.length() > 0) {
              buf.append("\n");
            }
            buf.append(msg);
          }
          return collectAllMessages(e.getCause(), processed);
        }
        return buf.toString();
      }
    }.collectAllMessages(e, new HashSet<Throwable>());
  }


  // Workaround for javac bug:
  // the internal ClientCodeWrapper class may not implement some interface-declared methods
  // which throw UnsupportedOperationException instead of delegating to our JpsFileManager instance
  private static JavaCompiler.CompilationTask tryInstallClientCodeWrapperCallDispatcher(JavaCompiler.CompilationTask task, StandardJavaFileManager delegateTo) {
    try {
      final Class<? extends JavaCompiler.CompilationTask> taskClass = task.getClass();
      final Field contextField = findField(taskClass, new BooleanFunction<Field>() {
        private final Class<?> contextClass = Class.forName("com.sun.tools.javac.util.Context", true, taskClass.getClassLoader());
        @Override
        public boolean fun(Field field) {
          return contextClass.equals(field.getType());
        }
      });
      if (contextField != null) {
        final Object contextObject = contextField.get(task);
        final Method getMethod = contextObject.getClass().getMethod("get", Class.class);
        final Object currentManager = getMethod.invoke(contextObject, JavaFileManager.class);
        if (isClientCodeWrapper(currentManager, delegateTo)) {
          final Method putMethod = contextObject.getClass().getMethod("put", Class.class, Object.class);
          putMethod.invoke(contextObject, JavaFileManager.class, null);  // must clear previous value first
          putMethod.invoke(contextObject, JavaFileManager.class, APIWrappers.wrap(StandardJavaFileManager.class, currentManager, Object.class, delegateTo));
        }
        else {
          installCallDispatcherRecursively(currentManager, delegateTo, new HashSet<>());
        }
      }
    }
    catch (Throwable ignored) {
    }
    return task;
  }

  private static void installCallDispatcherRecursively(final Object obj, final StandardJavaFileManager delegateTo, final Set<Object> visited) {
    if (obj instanceof JavaFileManager && visited.add(obj)) {
      forEachField(obj.getClass(), new BooleanFunction<Field>() {
        @Override
        public boolean fun(Field field) {
          try {
            if (JavaFileManager.class.isAssignableFrom(field.getType())) {
              final Object value = field.get(obj);
              if (isClientCodeWrapper(value, delegateTo)) {
                field.set(obj, APIWrappers.wrap(StandardJavaFileManager.class, value, Object.class, delegateTo));
              }
              else {
                installCallDispatcherRecursively(value, delegateTo, visited);
              }
            }
          }
          catch (Throwable ignored) {
          }
          return true;
        }
      });
    }
  }

  private static boolean isClientCodeWrapper(final Object obj, final StandardJavaFileManager delegateTo) {
    return obj instanceof StandardJavaFileManager && findField(obj.getClass(), new BooleanFunction<Field>() {
      @Override
      public boolean fun(Field f) {
        try {
          return f.get(obj) == delegateTo;
        }
        catch (Throwable ignored) {
          return false;
        }
      }
    }) != null;
  }

  private static Field findField(final Class<?> aClass, final BooleanFunction<? super Field> cond) {
    final Field[] res = new Field[]{null};
    forEachField(aClass, new BooleanFunction<Field>() {
      @Override
      public boolean fun(Field field) {
        if (!cond.fun(field)) {
          return true; // continue
        }
        res[0] = field;
        return false; // stop
      }
    });
    return res[0];
  }

  private static void forEachField(final Class<?> aClass, final BooleanFunction<? super Field> func) {
    for (Class<?> from = aClass; from != null && !Object.class.equals(from); from = from.getSuperclass()) {
      for (Field field : from.getDeclaredFields()) {
        try {
          if (!field.isAccessible()) {
            field.setAccessible(true);
          }
          if (!func.fun(field)) {
            return;
          }
        }
        catch (Throwable ignored) {
        }
      }
    }
  }

  private static void setLocation(StandardJavaFileManager fileManager, String locationId, Iterable<? extends File> path) throws IOException {
    JavaFileManager.Location location = StandardLocation.locationFor(locationId);
    if (location != null) { // if this option is supported
      fileManager.setLocation(location, path);
    }
  }

  private static Iterable<? extends File> getLocation(StandardJavaFileManager fileManager, String locationId) {
    final JavaFileManager.Location location = StandardLocation.locationFor(locationId);
    return location != null? fileManager.getLocation(location) : null;
  }

  private static boolean hasLocation(StandardJavaFileManager fileManager, String locationId) {
    final JavaFileManager.Location location = StandardLocation.locationFor(locationId);
    return location != null && fileManager.hasLocation(location);
  }

  private static boolean isAnnotationProcessingEnabled(final Iterable<String> options) {
    return !Iterators.contains(options, "-proc:none");
  }

  private static Iterable<String> prepareOptions(final Iterable<? extends String> options, @NotNull JavaCompilingTool compilingTool) {
    final List<String> result = new ArrayList<>(compilingTool.getDefaultCompilerOptions());
    boolean skip = false;
    for (String option : options) {
      if (FILTERED_OPTIONS.contains(option)) {
        skip = true;
        continue;
      }
      if (!skip) {
        if (!FILTERED_SINGLE_OPTIONS.contains(option) && !compilingTool.getDefaultCompilerOptions().contains(option)) {
          result.add(option);
        }
      }
      skip = false;
    }
    compilingTool.preprocessOptions(result);
    return result;
  }

  private static Iterable<? extends File> buildPlatformClasspath(Iterable<? extends File> platformClasspath, Iterable<String> options) {
    final Map<PathOption, String> argsMap = new HashMap<>();
    for (Iterator<String> iterator = options.iterator(); iterator.hasNext(); ) {
      final String arg = iterator.next();
      for (PathOption pathOption : PathOption.values()) {
        if (pathOption.parse(argsMap, arg, iterator)) {
          break;
        }
      }
    }
    if (argsMap.isEmpty()) {
      return platformClasspath;
    }

    final List<File> result = new ArrayList<>();
    appendFiles(argsMap, PathOption.PREPEND_CP, result, false);
    appendFiles(argsMap, PathOption.ENDORSED, result, true);
    appendFiles(argsMap, PathOption.D_ENDORSED, result, true);
    Iterators.collect(platformClasspath, result);
    appendFiles(argsMap, PathOption.APPEND_CP, result, false);
    appendFiles(argsMap, PathOption.EXTDIRS, result, true);
    appendFiles(argsMap, PathOption.D_EXTDIRS, result, true);
    return result;
  }

  private static void appendFiles(Map<PathOption, String> args, PathOption option, Collection<? super File> container, boolean listDir) {
    final String path = args.get(option);
    if (path == null) {
      return;
    }
    final StringTokenizer tokenizer = new StringTokenizer(path, File.pathSeparator, false);
    while (tokenizer.hasMoreTokens()) {
      final File file = new File(tokenizer.nextToken());
      if (listDir) {
        final File[] files = file.listFiles();
        if (files != null) {
          for (File f : files) {
            final String fName = f.getName();
            if (fName.endsWith(".jar") || fName.endsWith(".zip")) {
              container.add(f);
            }
          }
        }
      }
      else {
        container.add(file);
      }
    }
  }

  enum PathOption {
    PREPEND_CP("-Xbootclasspath/p:"),
    ENDORSED("-endorseddirs"), D_ENDORSED("-Djava.endorsed.dirs="),
    APPEND_CP("-Xbootclasspath/a:"),
    EXTDIRS("-extdirs"), D_EXTDIRS("-Djava.ext.dirs=");

    private final String myArgName;
    private final boolean myIsSuffix;

    PathOption(String name) {
      myArgName = name;
      myIsSuffix = name.endsWith("=") || name.endsWith(":");
    }

    public boolean parse(Map<PathOption, String> container, String arg, Iterator<String> rest) {
      if (myIsSuffix) {
        if (arg.startsWith(myArgName)) {
          container.put(this, arg.substring(myArgName.length()));
          return true;
        }
      }
      else {
        if (arg.equals(myArgName)) {
          if (rest.hasNext()) {
            container.put(this, rest.next());
          }
          return true;
        }
      }
      return false;
    }
  }

  private static final class ContextImpl implements JpsJavacFileManager.Context {
    private final StandardJavaFileManager myStdManager;
    private final DiagnosticOutputConsumer myOutConsumer;
    private final OutputFileConsumer myOutputFileSink;
    private final ModulePath myModulePath;
    private final CanceledStatus myCanceledStatus;

    ContextImpl(@NotNull JavaCompiler compiler,
                @NotNull DiagnosticOutputConsumer outConsumer,
                @NotNull OutputFileConsumer sink,
                @NotNull ModulePath modulePath,
                CanceledStatus canceledStatus) {
      myOutConsumer = outConsumer;
      myOutputFileSink = sink;
      myModulePath = modulePath;
      myCanceledStatus = canceledStatus;
      myStdManager = compiler.getStandardFileManager(outConsumer, Locale.US, null);
    }

    @Nullable
    @Override
    public String getExplodedAutomaticModuleName(File pathElement) {
      return myModulePath.getModuleName(pathElement);
    }

    @Override
    public boolean isCanceled() {
      return myCanceledStatus.isCanceled();
    }

    @NotNull
    @Override
    public StandardJavaFileManager getStandardFileManager() {
      return myStdManager;
    }

    @Override
    public void reportMessage(final Diagnostic.Kind kind, @Nls String message) {
      myOutConsumer.report(new PlainMessageDiagnostic(kind, message));
    }

    @Override
    public void consumeOutputFile(@NotNull final OutputFileObject cls) {
      myOutputFileSink.save(cls);
    }
  }

  private static final class NameTableCleanupDataHolder {
    static final Object emptyList;
    static final Field freelistField;

    static {
      try {
        final ClassLoader loader = ToolProvider.getSystemToolClassLoader();
        if (loader == null) {
          throw new RuntimeException("no tools provided");
        }

        final Class<?> listClass = Class.forName("com.sun.tools.javac.util.List", true, loader);
        final Method nilMethod = listClass.getDeclaredMethod("nil");
        emptyList = nilMethod.invoke(null);

        Field freelistRef;
        try {
          // trying jdk 6
          freelistRef = Class.forName("com.sun.tools.javac.util.Name$Table", true, loader).getDeclaredField("freelist");
        }
        catch (Exception e) {
          // trying jdk 7
          freelistRef = Class.forName("com.sun.tools.javac.util.SharedNameTable", true, loader).getDeclaredField("freelist");
        }
        freelistRef.setAccessible(true);
        freelistField = freelistRef;
      }
      catch(RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static void cleanupJavacNameTable() {
    try {
      final Field freelistField = NameTableCleanupDataHolder.freelistField;
      final Object emptyList = NameTableCleanupDataHolder.emptyList;
      // both parameters should be non-null if properly initialized
      if (freelistField != null && emptyList != null) {
        // the access to static 'freeList' field is synchronized inside javac, so we must use "synchronized" too
        synchronized (freelistField.getDeclaringClass()) {
          freelistField.set(null, emptyList);
        }
      }
    }
    catch (Throwable ignored) {
    }
  }

  private static final class ZipFileIndexCleanupDataHolder {
    @Nullable
    static final Method cacheInstanceGetter;
    @Nullable
    static final Method cacheClearMethod;

    static {
      Method getterMethod = null;
      Method clearMethod;
      try {
        //trying JDK 6
        clearMethod = Class.forName("com.sun.tools.javac.zip.ZipFileIndex").getDeclaredMethod("clearCache");
        clearMethod.setAccessible(true);
      }
      catch (Throwable e) {
        try {
          final Class<?> cacheClass = Class.forName("com.sun.tools.javac.file.ZipFileIndexCache");
          clearMethod = cacheClass.getDeclaredMethod("clearCache");
          getterMethod = cacheClass.getDeclaredMethod("getSharedInstance");
          clearMethod.setAccessible(true);
          getterMethod.setAccessible(true);
        }
        catch (Throwable ignored2) {
          clearMethod = null;
          getterMethod = null;
        }
      }

      cacheInstanceGetter = getterMethod;
      cacheClearMethod = clearMethod;
    }
  }

  private static volatile boolean zipCacheCleanupPossible = true;

  public static void clearCompilerZipFileCache() {
    if (zipCacheCleanupPossible) {
      final Method clearMethod = ZipFileIndexCleanupDataHolder.cacheClearMethod;
      if (clearMethod != null) {
        final Method getter = ZipFileIndexCleanupDataHolder.cacheInstanceGetter;
        try {
          Object instance = getter != null? getter.invoke(null) : null;
          clearMethod.invoke(instance);
        }
        catch (Throwable e) {
          zipCacheCleanupPossible = false;
        }
      }
      else {
        zipCacheCleanupPossible = false;
      }
    }
  }
}
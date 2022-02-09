// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.cl;

import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.lang.ClassPath;
import com.intellij.util.lang.ClasspathCache;
import com.intellij.util.lang.Resource;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

@ApiStatus.Internal
public final class PluginClassLoader extends UrlClassLoader implements PluginAwareClassLoader {
  public static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = new ClassLoader[0];

  static {
    boolean parallelCapable = registerAsParallelCapable();
    assert parallelCapable;
  }

  private static final @Nullable Writer logStream;
  private static final AtomicInteger instanceIdProducer = new AtomicInteger();
  private static final AtomicInteger parentListCacheIdCounter = new AtomicInteger();

  private static final Set<String> KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES;

  // avoid capturing reference to classloader in AccessControlContext
  private static final ProtectionDomain PROTECTION_DOMAIN = new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null);

  static {
    @SuppressWarnings("SSBasedInspection")
    Set<String> kotlinStdlibClassesUsedInSignatures = new HashSet<>(Arrays.asList(
      "kotlin.Function",
      "kotlin.sequences.Sequence",
      "kotlin.ranges.IntRange",
      "kotlin.ranges.IntRange$Companion",
      "kotlin.ranges.IntProgression",
      "kotlin.ranges.ClosedRange",
      "kotlin.ranges.IntProgressionIterator",
      "kotlin.ranges.IntProgression$Companion",
      "kotlin.ranges.IntProgression",
      "kotlin.collections.IntIterator",
      "kotlin.Lazy", "kotlin.Unit",
      "kotlin.Pair", "kotlin.Triple",
      "kotlin.jvm.internal.DefaultConstructorMarker",
      "kotlin.jvm.internal.ClassBasedDeclarationContainer",
      "kotlin.properties.ReadWriteProperty",
      "kotlin.properties.ReadOnlyProperty",
      "kotlin.coroutines.ContinuationInterceptor",
      "kotlinx.coroutines.CoroutineDispatcher",
      "kotlin.coroutines.Continuation",
      "kotlin.coroutines.CoroutineContext",
      "kotlin.coroutines.CoroutineContext$Element",
      "kotlin.coroutines.CoroutineContext$Key"
    ));
    String classes = System.getProperty("idea.kotlin.classes.used.in.signatures");
    if (classes != null) {
      for (StringTokenizer t = new StringTokenizer(classes, ","); t.hasMoreTokens(); ) {
        kotlinStdlibClassesUsedInSignatures.add(t.nextToken());
      }
    }
    KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES = kotlinStdlibClassesUsedInSignatures;

    Writer logStreamCandidate = null;
    String debugFilePath = System.getProperty("plugin.classloader.debug", "");
    if (!debugFilePath.isEmpty()) {
      try {
        if (debugFilePath.startsWith("~/") || debugFilePath.startsWith("~\\")) {
          debugFilePath = System.getProperty("user.home") + debugFilePath.substring(1);
        }

        logStreamCandidate = Files.newBufferedWriter(Paths.get(debugFilePath));
        ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
          @Override
          public void run() {
            try {
              if (logStream != null) {
                logStream.close();
              }
            }
            catch (IOException e) {
              Logger.getInstance(PluginClassLoader.class).error(e);
            }
          }
        });
      }
      catch (IOException e) {
        Logger.getInstance(PluginClassLoader.class).error(e);
      }
    }

    logStream = logStreamCandidate;
  }

  private final IdeaPluginDescriptorImpl[] parents;

  // cache of computed list of all parents (not only direct)
  private volatile ClassLoader[] allParents;
  private volatile int allParentsLastCacheId;

  private final PluginDescriptor pluginDescriptor;
  // to simplify analyzing of heap dump (dynamic plugin reloading)
  private final PluginId pluginId;
  private final String packagePrefix;
  private final List<String> libDirectories;

  private final AtomicLong edtTime = new AtomicLong();
  private final AtomicLong backgroundTime = new AtomicLong();

  private final AtomicInteger loadedClassCounter = new AtomicInteger();
  private final @NotNull ClassLoader coreLoader;

  private final int instanceId;
  private volatile int state = ACTIVE;

  @SuppressWarnings("FieldNameHidesFieldInSuperclass")
  private final ResolveScopeManager resolveScopeManager;

  public interface ResolveScopeManager {
    String isDefinitelyAlienClass(String name, String packagePrefix, boolean force);
  }

  public PluginClassLoader(@NotNull List<Path> files,
                           @NotNull ClassPath classPath,
                           @NotNull IdeaPluginDescriptorImpl @NotNull [] dependencies,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @NotNull ClassLoader coreLoader,
                           @Nullable ResolveScopeManager resolveScopeManager,
                           @Nullable String packagePrefix,
                           @NotNull List<String> libDirectories) {
    super(files, classPath);

    instanceId = instanceIdProducer.incrementAndGet();

    this.resolveScopeManager = resolveScopeManager == null ? (p1, p2, p3) -> null : resolveScopeManager;
    this.parents = dependencies;
    this.pluginDescriptor = pluginDescriptor;
    pluginId = pluginDescriptor.getPluginId();
    this.packagePrefix = (packagePrefix == null || packagePrefix.endsWith(".")) ? packagePrefix : (packagePrefix + '.');
    this.coreLoader = coreLoader;
    this.libDirectories = libDirectories;
  }

  public @NotNull List<String> getLibDirectories() {
    return libDirectories;
  }

  @Override
  public @Nullable String getPackagePrefix() {
    return packagePrefix;
  }

  @Override
  @ApiStatus.Internal
  public int getState() {
    return state;
  }

  @ApiStatus.Internal
  public void setState(int state) {
    this.state = state;
  }

  @Override
  public int getInstanceId() {
    return instanceId;
  }

  @Override
  public long getEdtTime() {
    return edtTime.get();
  }

  @Override
  public long getBackgroundTime() {
    return backgroundTime.get();
  }

  @Override
  public long getLoadedClassCount() {
    return loadedClassCounter.get();
  }

  @Override
  public Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
    Class<?> c = tryLoadingClass(name, false);
    if (c == null) {
      flushDebugLog();
      throw new ClassNotFoundException(name + " " + this);
    }
    return c;
  }

  /**
   * See https://stackoverflow.com/a/5428795 about resolve flag.
   */
  @Override
  public @Nullable Class<?> tryLoadingClass(@NotNull String name, boolean forceLoadFromSubPluginClassloader)
    throws ClassNotFoundException {
    if (mustBeLoadedByPlatform(name)) {
      return coreLoader.loadClass(name);
    }

    String fileNameWithoutExtension = name.replace('.', '/');
    String fileName = fileNameWithoutExtension + ClasspathCache.CLASS_EXTENSION;
    long packageNameHash = ClasspathCache.getPackageNameHash(fileNameWithoutExtension, fileNameWithoutExtension.lastIndexOf('/'));

    long startTime = StartUpMeasurer.measuringPluginStartupCosts ? StartUpMeasurer.getCurrentTime() : -1;
    Class<?> c;
    PluginException error = null;
    try {
      String consistencyError = resolveScopeManager.isDefinitelyAlienClass(name, packagePrefix, forceLoadFromSubPluginClassloader);
      if (consistencyError == null) {
        c = loadClassInsideSelf(name, fileName, packageNameHash, forceLoadFromSubPluginClassloader);
      }
      else {
        if (!consistencyError.isEmpty()) {
          error = new PluginException(consistencyError, pluginId);
        }
        c = null;
      }
    }
    catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }

    if (c == null) {
      for (ClassLoader classloader : getAllParents()) {
        if (classloader instanceof PluginClassLoader) {
          try {
            PluginClassLoader pluginClassLoader = (PluginClassLoader)classloader;
            String consistencyError = pluginClassLoader.resolveScopeManager.isDefinitelyAlienClass(name,
                                                                                                   pluginClassLoader.packagePrefix,
                                                                                                   forceLoadFromSubPluginClassloader);
            if (consistencyError != null) {
              if (!consistencyError.isEmpty() && error == null) {
                // yes, we blame requestor plugin
                error = new PluginException(consistencyError, pluginId);
              }
              continue;
            }
            c = pluginClassLoader.loadClassInsideSelf(name, fileName, packageNameHash, false);
          }
          catch (IOException e) {
            throw new ClassNotFoundException(name, e);
          }
          if (c != null) {
            break;
          }
        }
        else if (classloader instanceof UrlClassLoader) {
          try {
            UrlClassLoader urlClassLoader = (UrlClassLoader)classloader;
            BiFunction<String, Boolean, String> resolveScopeManager = urlClassLoader.resolveScopeManager;
            String consistencyError = resolveScopeManager == null
                                      ? null
                                      : resolveScopeManager.apply(name, forceLoadFromSubPluginClassloader);
            if (consistencyError != null) {
              if (!consistencyError.isEmpty() && error == null) {
                // yes, we blame requestor plugin
                error = new PluginException(consistencyError, pluginId);
              }
              continue;
            }
            c = urlClassLoader.loadClassInsideSelf(name, fileName, packageNameHash, false);
          }
          catch (IOException e) {
            throw new ClassNotFoundException(name, e);
          }
          if (c != null) {
            break;
          }
        }
        else {
          try {
            c = classloader.loadClass(name);
            if (c != null) {
              break;
            }
          }
          catch (ClassNotFoundException ignoreAndContinue) {
            // ignore and continue
          }
        }
      }

      if (error != null) {
        throw error;
      }
    }

    if (startTime != -1) {
      // EventQueue.isDispatchThread() is expensive
      (EDT.isCurrentThreadEdt() ? edtTime : backgroundTime).addAndGet(StartUpMeasurer.getCurrentTime() - startTime);
    }

    return c;
  }

  private @NotNull ClassLoader @NotNull[] getAllParents() {
    ClassLoader[] result = allParents;
    if (result != null && allParentsLastCacheId == parentListCacheIdCounter.get()) {
      return result;
    }

    if (parents.length == 0) {
      result = new ClassLoader[]{coreLoader};
      allParents = result;
      return result;
    }

    Set<ClassLoader> parentSet = new LinkedHashSet<>();
    Deque<ClassLoader> queue = new ArrayDeque<>();
    collectClassLoaders(queue);
    ClassLoader classLoader;
    while ((classLoader = queue.pollFirst()) != null) {
      if (!parentSet.add(classLoader)) {
        continue;
      }

      if (classLoader instanceof PluginClassLoader) {
        ((PluginClassLoader)classLoader).collectClassLoaders(queue);
      }
    }
    parentSet.add(coreLoader);
    result = parentSet.toArray(EMPTY_CLASS_LOADER_ARRAY);
    allParents = result;
    allParentsLastCacheId = parentListCacheIdCounter.get();
    return result;
  }

  private void collectClassLoaders(@NotNull Deque<ClassLoader> queue) {
    for (IdeaPluginDescriptorImpl parent : parents) {
      ClassLoader classLoader = parent.getPluginClassLoader();
      if (classLoader != null && classLoader != coreLoader) {
        queue.add(classLoader);
      }
    }
  }

  public void clearParentListCache() {
    allParents = null;
  }

  private static boolean mustBeLoadedByPlatform(@NonNls String className) {
    if (className.startsWith("java.")) {
      return true;
    }

    // some commonly used classes from kotlin-runtime must be loaded by the platform classloader. Otherwise, if a plugin bundles its own version
    // of kotlin-runtime.jar it won't be possible to call platform's methods with these types in signatures from such a plugin.
    // We assume that these classes don't change between Kotlin versions, so it's safe to always load them from platform's kotlin-runtime.
    return className.startsWith("kotlin.") && (className.startsWith("kotlin.jvm.functions.") ||
                                               (className.startsWith("kotlin.reflect.") &&
                                                className.indexOf('.', 15 /* "kotlin.reflect".length */) < 0) ||
                                               KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES.contains(className));
  }

  @Override
  public boolean hasLoadedClass(String name) {
    String consistencyError = resolveScopeManager.isDefinitelyAlienClass(name, packagePrefix, false);
    return consistencyError == null && super.hasLoadedClass(name);
  }

  @Override
  public @Nullable Class<?> loadClassInsideSelf(String name,
                                                String fileName,
                                                long packageNameHash,
                                                boolean forceLoadFromSubPluginClassloader) throws IOException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c != null && c.getClassLoader() == this) {
        return c;
      }

      Writer logStream = PluginClassLoader.logStream;
      try {
        c = classPath.findClass(name, fileName, packageNameHash, classDataConsumer);
      }
      catch (LinkageError e) {
        if (logStream != null) {
          logClass(name, logStream, e);
        }
        flushDebugLog();
        throw new PluginException("Cannot load class " + name + " (" +
                                  "\n  error: " + e.getMessage() +
                                  ",\n  classLoader=" + this + "\n)", e, pluginId);
      }

      if (c == null) {
        return null;
      }

      loadedClassCounter.incrementAndGet();
      if (logStream != null) {
        logClass(name, logStream, null);
      }
      return c;
    }
  }

  private void logClass(@NotNull String name, @NotNull Writer logStream, @Nullable LinkageError exception) {
    try {
      // must be as one write call since write is performed from multiple threads
      String descriptorPath = ((IdeaPluginDescriptor)pluginDescriptor).getDescriptorPath();
      String specifier = descriptorPath == null ? "m" : "sub = " + descriptorPath;
      logStream.write(name + " [" + specifier + "] " + pluginId.getIdString() + (packagePrefix == null ? "" : (':' + packagePrefix))
                      + '\n' + (exception == null ? "" : exception.getMessage()));
    }
    catch (IOException ignored) {
    }
  }

  @Override
  public @Nullable URL findResource(@NotNull String name) {
    return doFindResource(name, Resource::getURL, ClassLoader::getResource);
  }

  @Override
  public byte @Nullable [] getResourceAsBytes(@NotNull String name, boolean checkParents) throws IOException {
    byte[] result = super.getResourceAsBytes(name, checkParents);
    if (result != null) {
      return result;
    }

    if (!checkParents) {
      return null;
    }

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof UrlClassLoader) {
        Resource resource = ((UrlClassLoader)classloader).getClassPath().findResource(name);
        if (resource != null) {
          return resource.getBytes();
        }
      }
      else {
        InputStream input = classloader.getResourceAsStream(name);
        if (input != null) {
          try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int read;
            byte[] data = new byte[16384];
            while ((read = input.read(data, 0, data.length)) != -1) {
              buffer.write(data, 0, read);
            }
            return buffer.toByteArray();
          }
          finally {
            input.close();
          }
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable InputStream getResourceAsStream(@NotNull String name) {
    Function<Resource, InputStream> f1 = resource -> {
      try {
        return resource.getInputStream();
      }
      catch (IOException e) {
        Logger.getInstance(PluginClassLoader.class).error(e);
        return null;
      }
    };
    BiFunction<ClassLoader, String, InputStream> f2 = (cl, path) -> {
      try {
        return cl.getResourceAsStream(path);
      }
      catch (Exception e) {
        Logger.getInstance(PluginClassLoader.class).error(e);
        return null;
      }
    };
    return doFindResource(name, f1, f2);
  }

  private <T> @Nullable T doFindResource(String name, Function<Resource, T> f1, BiFunction<ClassLoader, String, T> f2) {
    String canonicalPath = toCanonicalPath(name);

    Resource resource = classPath.findResource(canonicalPath);
    if (resource != null) {
      return f1.apply(resource);
    }

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof PluginClassLoader) {
        resource = ((PluginClassLoader)classloader).classPath.findResource(canonicalPath);
        if (resource != null) {
          return f1.apply(resource);
        }
      }
      else {
        T t = f2.apply(classloader, canonicalPath);
        if (t != null) {
          return t;
        }
      }
    }

    if (canonicalPath.startsWith("/") && classPath.findResource(canonicalPath.substring(1)) != null) {
      // reporting malformed paths only when there's a resource at the right one - which is rarely the case
      // (see also `UrlClassLoader#doFindResource`)
      String message = "Calling `ClassLoader#getResource` with leading slash doesn't work; strip";
      Logger.getInstance(PluginClassLoader.class).error(message, new PluginException(name, pluginId));
    }

    return null;
  }

  @Override
  public @NotNull Enumeration<URL> findResources(@NotNull String name) throws IOException {
    List<Enumeration<URL>> resources = new ArrayList<>();
    resources.add(classPath.getResources(name));
    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof PluginClassLoader) {
        resources.add(((PluginClassLoader)classloader).classPath.getResources(name));
      }
      else {
        try {
          resources.add(classloader.getResources(name));
        }
        catch (IOException ignore) { }
      }
    }
    return new DeepEnumeration(resources);
  }

  @SuppressWarnings("UnusedDeclaration")
  public void addLibDirectories(@NotNull Collection<String> libDirectories) {
    this.libDirectories.addAll(libDirectories);
  }

  @Override
  protected String findLibrary(String libName) {
    if (!libDirectories.isEmpty()) {
      String libFileName = System.mapLibraryName(libName);
      ListIterator<String> i = libDirectories.listIterator(libDirectories.size());
      while (i.hasPrevious()) {
        File libFile = new File(i.previous(), libFileName);
        if (libFile.exists()) {
          return libFile.getAbsolutePath();
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull PluginId getPluginId() {
    return pluginId;
  }

  @Override
  public @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(plugin=" + pluginDescriptor +
           ", packagePrefix=" + packagePrefix +
           ", instanceId=" + instanceId +
           ", state=" + (state == ACTIVE ? "active" : "unload in progress") +
           ")";
  }

  private static final class DeepEnumeration implements Enumeration<URL> {
    private final @NotNull List<Enumeration<URL>> list;
    private int myIndex;

    DeepEnumeration(@NotNull List<Enumeration<URL>> enumerations) {
      list = enumerations;
    }

    @Override
    public boolean hasMoreElements() {
      while (myIndex < list.size()) {
        Enumeration<URL> e = list.get(myIndex);
        if (e != null && e.hasMoreElements()) {
          return true;
        }
        myIndex++;
      }
      return false;
    }

    @Override
    public URL nextElement() {
      if (!hasMoreElements()) {
        throw new NoSuchElementException();
      }
      return list.get(myIndex).nextElement();
    }
  }

  @TestOnly
  public @NotNull List<IdeaPluginDescriptorImpl> _getParents() {
    //noinspection SSBasedInspection
    return Collections.unmodifiableList(Arrays.asList(parents));
  }

  @Override
  protected ProtectionDomain getProtectionDomain() {
    return PROTECTION_DOMAIN;
  }

  private static void flushDebugLog() {
    if (logStream != null) {
      try {
        logStream.flush();
      }
      catch (IOException ignore) { }
    }
  }
}

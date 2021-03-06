// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.cl;

import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.SmartList;
import com.intellij.util.lang.ClassPath;
import com.intellij.util.lang.Resource;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
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
@ApiStatus.NonExtendable
public class PluginClassLoader extends UrlClassLoader implements PluginAwareClassLoader {
  public static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = new ClassLoader[0];

  private static final boolean isParallelCapable = USE_PARALLEL_LOADING && registerAsParallelCapable();

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

  private ClassLoader[] parents;
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

  private final ResolveScopeManager resolveScopeManager;

  public interface ResolveScopeManager {
    boolean isDefinitelyAlienClass(String name, String packagePrefix, boolean force);
  }

  public PluginClassLoader(@NotNull UrlClassLoader.Builder builder,
                           @NotNull ClassLoader @NotNull [] parents,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @Nullable Path pluginRoot,
                           @NotNull ClassLoader coreLoader,
                           @Nullable ResolveScopeManager resolveScopeManager,
                           @Nullable String packagePrefix,
                           @Nullable ClassPath.ResourceFileFactory resourceFileFactory) {
    super(builder, resourceFileFactory, isParallelCapable);

    instanceId = instanceIdProducer.incrementAndGet();

    this.resolveScopeManager = resolveScopeManager == null ? (p1, p2, p3) -> false : resolveScopeManager;
    this.parents = parents;
    this.pluginDescriptor = pluginDescriptor;
    pluginId = pluginDescriptor.getPluginId();
    this.packagePrefix = (packagePrefix == null || packagePrefix.endsWith(".")) ? packagePrefix : (packagePrefix + '.');
    this.coreLoader = coreLoader;
    if (PluginClassLoader.class.desiredAssertionStatus()) {
      for (ClassLoader parent : this.parents) {
        if (parent == coreLoader) {
          Logger.getInstance(PluginClassLoader.class).error("Core loader must be not specified in parents " +
                                                            "(parents=" + Arrays.toString(parents) + ", coreLoader=" + coreLoader + ")");
        }
      }
    }

    libDirectories = new SmartList<>();
    if (pluginRoot != null) {
      Path libDir = pluginRoot.resolve("lib");
      if (Files.exists(libDir)) {
        libDirectories.add(libDir.toAbsolutePath().toString());
      }
    }
  }

  @Override
  public final @Nullable String getPackagePrefix() {
    return packagePrefix;
  }

  @Override
  @ApiStatus.Internal
  public final int getState() {
    return state;
  }

  @ApiStatus.Internal
  public final void setState(int state) {
    this.state = state;
  }

  @Override
  public final int getInstanceId() {
    return instanceId;
  }

  @Override
  public final long getEdtTime() {
    return edtTime.get();
  }

  @Override
  public final long getBackgroundTime() {
    return backgroundTime.get();
  }

  @Override
  public final long getLoadedClassCount() {
    return loadedClassCounter.get();
  }

  @Override
  public final Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
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
  public final @Nullable Class<?> tryLoadingClass(@NotNull String name, boolean forceLoadFromSubPluginClassloader)
    throws ClassNotFoundException {
    if (mustBeLoadedByPlatform(name)) {
      return coreLoader.loadClass(name);
    }

    long startTime = StartUpMeasurer.measuringPluginStartupCosts ? StartUpMeasurer.getCurrentTime() : -1;
    Class<?> c;
    try {
      c = loadClassInsideSelf(name, forceLoadFromSubPluginClassloader);
    }
    catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }

    if (c == null) {
      for (ClassLoader classloader : getAllParents()) {
        if (classloader instanceof UrlClassLoader) {
          try {
            c = ((UrlClassLoader)classloader).loadClassInsideSelf(name, false);
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
    Collections.addAll(queue, parents);
    ClassLoader classLoader;
    while ((classLoader = queue.pollFirst()) != null) {
      if (classLoader == coreLoader || !parentSet.add(classLoader)) {
        continue;
      }

      if (classLoader instanceof PluginClassLoader) {
        Collections.addAll(queue, ((PluginClassLoader)classLoader).parents);
      }
    }
    parentSet.add(coreLoader);
    result = parentSet.toArray(EMPTY_CLASS_LOADER_ARRAY);
    allParents = result;
    allParentsLastCacheId = parentListCacheIdCounter.get();
    return result;
  }

  public final void clearParentListCache() {
    allParents = null;
  }

  private static boolean mustBeLoadedByPlatform(@NonNls String className) {
    if (className.startsWith("java.")) {
      return true;
    }

    // some commonly used classes from kotlin-runtime must be loaded by the platform classloader. Otherwise if a plugin bundles its own version
    // of kotlin-runtime.jar it won't be possible to call platform's methods with these types in signatures from such a plugin.
    // We assume that these classes don't change between Kotlin versions so it's safe to always load them from platform's kotlin-runtime.
    return className.startsWith("kotlin.") && (className.startsWith("kotlin.jvm.functions.") ||
                                               (className.startsWith("kotlin.reflect.") &&
                                                className.indexOf('.', 15 /* "kotlin.reflect".length */) < 0) ||
                                               KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES.contains(className));
  }

  @Override
  public @Nullable Class<?> loadClassInsideSelf(@NotNull String name, boolean forceLoadFromSubPluginClassloader) throws IOException {
    if (resolveScopeManager.isDefinitelyAlienClass(name, packagePrefix, forceLoadFromSubPluginClassloader)) {
      return null;
    }

    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c != null && c.getClassLoader() == this) {
        return c;
      }

      Writer logStream = PluginClassLoader.logStream;
      try {
        c = classPath.findClass(name);
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
  public final @Nullable URL findResource(@NotNull String name) {
    return findResource(name, Resource::getURL, ClassLoader::getResource);
  }

  @Override
  public final @Nullable InputStream getResourceAsStream(@NotNull String name) {
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
    return findResource(name, f1, f2);
  }

  private <T> @Nullable T findResource(String name, Function<Resource, T> f1, BiFunction<ClassLoader, String, T> f2) {
    String canonicalPath = toCanonicalPath(name);

    if (canonicalPath.startsWith("/")) {
      canonicalPath = canonicalPath.substring(1);
      //noinspection SpellCheckingInspection
      if (!canonicalPath.startsWith("/org/bridj/")) {
        String message = "Do not request resource from classloader using path with leading slash";
        Logger.getInstance(PluginClassLoader.class).error(message, new PluginException(name, pluginId));
      }
    }

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

    return null;
  }

  @Override
  public final @NotNull Enumeration<URL> findResources(@NotNull String name) throws IOException {
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
  public final void addLibDirectories(@NotNull Collection<String> libDirectories) {
    this.libDirectories.addAll(libDirectories);
  }

  @Override
  protected final String findLibrary(String libName) {
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
  public final @NotNull PluginId getPluginId() {
    return pluginId;
  }

  @Override
  public final @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public final String toString() {
    return getClass().getSimpleName() + "(plugin=" + pluginDescriptor +
           ", packagePrefix=" + packagePrefix +
           ", instanceId=" + instanceId +
           ", state=" + (state == ACTIVE ? "active" : "unload in progress") +
           ")";
  }

  private static final class DeepEnumeration implements Enumeration<URL> {
    private final @NotNull List<? extends Enumeration<URL>> list;
    private int myIndex;

    DeepEnumeration(@NotNull List<? extends Enumeration<URL>> enumerations) {
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
  @ApiStatus.Internal
  public final @NotNull List<ClassLoader> _getParents() {
    //noinspection SSBasedInspection
    return Collections.unmodifiableList(Arrays.asList(parents));
  }

  @ApiStatus.Internal
  public final void attachParent(@NotNull ClassLoader classLoader) {
    int length = parents.length;
    ClassLoader[] result = new ClassLoader[length + 1];
    System.arraycopy(parents, 0, result, 0, length);
    result[length] = classLoader;
    parents = result;
    parentListCacheIdCounter.incrementAndGet();
  }

  /**
   * You must clear allParents cache for all loaded plugins.
   */
  @ApiStatus.Internal
  public final boolean detachParent(@NotNull ClassLoader classLoader) {
    for (int i = 0; i < parents.length; i++) {
      if (classLoader != parents[i]) {
        continue;
      }

      int length = parents.length;
      ClassLoader[] result = new ClassLoader[length - 1];
      System.arraycopy(parents, 0, result, 0, i);
      System.arraycopy(parents, i + 1, result, i, length - i - 1);
      parents = result;
      parentListCacheIdCounter.incrementAndGet();
      return true;
    }
    return false;
  }

  @Override
  protected final ProtectionDomain getProtectionDomain() {
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

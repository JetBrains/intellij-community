// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.cl;

import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.*;

import java.awt.*;
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
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ApiStatus.Internal
@ApiStatus.NonExtendable
public class PluginClassLoader extends UrlClassLoader implements PluginAwareClassLoader {
  public static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = new ClassLoader[0];

  private static final @Nullable Writer logStream;
  private static final AtomicInteger instanceIdProducer = new AtomicInteger();
  private static final AtomicInteger parentListCacheIdCounter = new AtomicInteger();

  private static final Set<String> KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES;

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

    if (registerAsParallelCapable()) {
      markParallelCapable(PluginClassLoader.class);
    }

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

  public PluginClassLoader(@NotNull Builder builder,
                           @NotNull ClassLoader @NotNull [] parents,
                           @NotNull PluginDescriptor pluginDescriptor,
                           @Nullable Path pluginRoot,
                           @NotNull ClassLoader coreLoader,
                           @Nullable String packagePrefix) {
    super(builder);

    instanceId = instanceIdProducer.incrementAndGet();

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
    Class<?> c = loadClassInsideSelf(name, forceLoadFromSubPluginClassloader);
    if (c == null) {
      for (ClassLoader classloader : getAllParents()) {
        if (classloader instanceof PluginClassLoader) {
          c = ((PluginClassLoader)classloader).loadClassInsideSelf(name, false);
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
      Application app = ApplicationManager.getApplication();
      // JDK impl is not so fast as ours, use it only if no application
      boolean isEdt = app == null ? EventQueue.isDispatchThread() : app.isDispatchThread();
      (isEdt ? edtTime : backgroundTime).addAndGet(StartUpMeasurer.getCurrentTime() - startTime);
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

  protected @Nullable Class<?> loadClassInsideSelf(@NotNull String name, boolean forceLoadFromSubPluginClassloader) {
    if (packagePrefix != null && isDefinitelyAlienClass(name, packagePrefix)) {
      return null;
    }

    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c != null && c.getClassLoader() == this) {
        return c;
      }

      try {
        c = _findClass(name);
      }
      catch (LinkageError e) {
        flushDebugLog();
        throw new PluginException("While loading class " + name + ": " + e.getMessage(), e, pluginId);
      }

      if (c == null) {
        return null;
      }

      loadedClassCounter.incrementAndGet();
      Writer logStream = PluginClassLoader.logStream;
      if (logStream != null) {
        try {
          // must be as one write call since write is performed from multiple threads
          String specifier = getClass() == PluginClassLoader.class ? "m" : "s = " + ((IdeaPluginDescriptor)pluginDescriptor).getDescriptorPath();
          logStream.write(name + " [" + specifier + "] " + pluginId.getIdString() + (packagePrefix == null ? "" : (':' + packagePrefix)) + '\n');
        }
        catch (IOException ignored) {
        }
      }

      return c;
    }
  }

  protected boolean isDefinitelyAlienClass(@NotNull String name, @NotNull String packagePrefix) {
    // packed into plugin jar
    return !name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier");
  }

  @Override
  public final URL findResource(String name) {
    URL resource = findOwnResource(name);
    if (resource != null) {
      return resource;
    }

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof PluginClassLoader) {
        resource = ((PluginClassLoader)classloader).findOwnResource(name);
      }
      else {
        resource = classloader.getResource(name);
      }

      if (resource != null) {
        return resource;
      }
    }

    return null;
  }

  private @Nullable URL findOwnResource(String name) {
    return super.findResource(name);
  }

  @Override
  public final InputStream getResourceAsStream(String name) {
    InputStream stream = getOwnResourceAsStream(name);
    if (stream != null) {
      return stream;
    }

    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof PluginClassLoader) {
        stream = ((PluginClassLoader)classloader).getOwnResourceAsStream(name);
      }
      else {
        stream = classloader.getResourceAsStream(name);
      }

      if (stream != null) {
        return stream;
      }
    }

    return null;
  }

  private @Nullable InputStream getOwnResourceAsStream(String name) {
    return super.getResourceAsStream(name);
  }

  @Override
  public final Enumeration<URL> findResources(String name) throws IOException {
    List<Enumeration<URL>> resources = new ArrayList<>();
    resources.add(findOwnResources(name));
    for (ClassLoader classloader : getAllParents()) {
      if (classloader instanceof PluginClassLoader) {
        try {
          resources.add(((PluginClassLoader)classloader).findOwnResources(name));
        }
        catch (IOException ignore) {
        }
      }
      else {
        try {
          resources.add(classloader.getResources(name));
        }
        catch (IOException ignore) {
        }
      }
    }
    return new DeepEnumeration(resources);
  }

  private Enumeration<URL> findOwnResources(String name) throws IOException {
    return super.findResources(name);
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
    private final List<Enumeration<URL>> list;
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
  @ApiStatus.Internal
  public final @NotNull List<ClassLoader> _getParents() {
    //noinspection SSBasedInspection
    return Collections.unmodifiableList(Arrays.asList(parents));
  }

  @ApiStatus.Internal
  public final void attachParent(@NotNull ClassLoader classLoader) {
    parents = ArrayUtil.append(parents, classLoader);
    parentListCacheIdCounter.incrementAndGet();
  }

  /**
   * You must clear allParents cache for all loaded plugins.
   */
  @ApiStatus.Internal
  public final boolean detachParent(@NotNull ClassLoader classLoader) {
    int oldSize = parents.length;
    parents = ArrayUtil.remove(parents, classLoader);
    parentListCacheIdCounter.incrementAndGet();
    return parents.length == oldSize - 1;
  }

  @Override
  protected final ProtectionDomain getProtectionDomain(URL url) {
    // avoid capturing reference to classloader in AccessControlContext
    return new ProtectionDomain(new CodeSource(url, (Certificate[])null), null);
  }

  private static void flushDebugLog() {
    if (logStream != null) {
      try {
        logStream.flush();
      }
      catch (IOException ignore) {
      }
    }
  }
}
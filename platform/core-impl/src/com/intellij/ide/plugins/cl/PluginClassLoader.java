// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.cl;

import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class PluginClassLoader extends UrlClassLoader {
  static {
    if (registerAsParallelCapable()) {
      markParallelCapable(PluginClassLoader.class);
    }
  }

  private ClassLoader[] myParents;
  private final IdeaPluginDescriptor myPluginDescriptor;
  private final List<String> myLibDirectories;

  private final AtomicLong edtTime = new AtomicLong();
  private final AtomicLong backgroundTime = new AtomicLong();

  private final AtomicInteger loadedClassCounter = new AtomicInteger();
  private ClassLoader myCoreLoader;

  // to simplify analyzing of heap dump (dynamic plugin reloading)
  private final PluginId pluginId;

  public PluginClassLoader(@NotNull List<URL> urls,
                           @NotNull ClassLoader @NotNull [] parents,
                           @NotNull IdeaPluginDescriptor pluginDescriptor,
                           @Nullable Path pluginRoot) {
    this(build().urls(urls).allowLock().useCache(), parents, pluginDescriptor, pluginRoot);
  }

  public PluginClassLoader(@NotNull Builder builder,
                           @NotNull ClassLoader @NotNull [] parents,
                           @NotNull IdeaPluginDescriptor pluginDescriptor,
                           @Nullable Path pluginRoot) {
    super(builder);

    myParents = parents;
    myPluginDescriptor = pluginDescriptor;
    pluginId = pluginDescriptor.getPluginId();

    myLibDirectories = new SmartList<>();
    if (pluginRoot != null) {
      Path libDir = pluginRoot.resolve("lib");
      if (Files.exists(libDir)) {
        myLibDirectories.add(libDir.toAbsolutePath().toString());
      }
    }
  }

  public long getEdtTime() {
    return edtTime.get();
  }

  public long getBackgroundTime() {
    return backgroundTime.get();
  }

  public long getLoadedClassCount() {
    return loadedClassCounter.get();
  }

  @Override
  public Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
    Class<?> c = tryLoadingClass(name, resolve, null, true);
    if (c == null) {
      throw new ClassNotFoundException(name + " " + this);
    }
    return c;
  }

  private interface ActionWithClassloader<Result, ParameterType> {
    Result execute(String name, ClassLoader classloader, ParameterType parameter);
  }

  private abstract static class ActionWithPluginClassLoader<Result, ParameterType> {
    Result execute(String name, PluginClassLoader classloader, Set<ClassLoader> visited,
                   ActionWithPluginClassLoader<Result, ParameterType> actionWithPluginClassLoader,
                   ActionWithClassloader<Result, ParameterType> actionWithClassloader,
                   ParameterType parameter) {
      Result resource = doExecute(name, classloader, parameter);
      if (resource != null) return resource;
      return classloader.processResourcesInParents(name, actionWithPluginClassLoader, actionWithClassloader, visited, parameter, false);
    }

    protected abstract Result doExecute(String name, PluginClassLoader classloader, ParameterType parameter);

  }

  private @Nullable <Result, ParameterType> Result processResourcesInParents(String name,
                                                                             ActionWithPluginClassLoader<Result, ParameterType> actionWithPluginClassLoader,
                                                                             ActionWithClassloader<Result, ParameterType> actionWithClassloader,
                                                                             ParameterType parameter) {
    return processResourcesInParents(name, actionWithPluginClassLoader, actionWithClassloader, null, parameter, true);
  }

  public void setCoreLoader(ClassLoader loader) {
    myCoreLoader = loader;
  }

  private @Nullable <Result, ParameterType> Result processResourcesInParents(String name,
                                                                             ActionWithPluginClassLoader<Result, ParameterType> actionWithPluginClassLoader,
                                                                             ActionWithClassloader<Result, ParameterType> actionWithClassloader,
                                                                             Set<ClassLoader> visited,
                                                                             ParameterType parameter,
                                                                             boolean withRoot) {
    for (ClassLoader parent : myParents) {
      if (parent == myCoreLoader) {
        continue;
      }
      if (visited == null) {
        visited = new HashSet<>();
        visited.add(this);
      }

      if (!visited.add(parent)) {
        continue;
      }

      if (parent instanceof PluginClassLoader) {
        Result resource = actionWithPluginClassLoader.execute(name, (PluginClassLoader)parent, visited, actionWithPluginClassLoader,
          actionWithClassloader, parameter);
        if (resource != null) {
          return resource;
        }
        continue;
      }

      Result resource = actionWithClassloader.execute(name, parent, parameter);
      if (resource != null) return resource;
    }

    if (withRoot && myCoreLoader != null && (visited == null || visited.add(myCoreLoader))) {
      return actionWithClassloader.execute(name, myCoreLoader, parameter);
    }

    return null;
  }

  private static final ActionWithPluginClassLoader<Class<?>, Void> loadClassInPluginCL = new ActionWithPluginClassLoader<Class<?>, Void>() {
    @Override
    Class<?> execute(String name,
                     PluginClassLoader classloader,
                     Set<ClassLoader> visited,
                     ActionWithPluginClassLoader<Class<?>, Void> actionWithPluginClassLoader,
                     ActionWithClassloader<Class<?>, Void> actionWithClassloader,
                     Void parameter) {
      return classloader.tryLoadingClass(name, false, visited, false);
    }

    @Override
    protected Class<?> doExecute(String name, PluginClassLoader classloader, Void parameter) {
      return null;
    }
  };

  private static final ActionWithClassloader<Class<?>, Void> loadClassInCl = new ActionWithClassloader<Class<?>, Void>() {
    @Override
    public Class<?> execute(String name, ClassLoader classloader, Void parameter) {
      try {
        return classloader.loadClass(name);
      }
      catch (ClassNotFoundException ignoreAndContinue) {
        // Ignore and continue
      }
      return null;
    }
  };

  // Changed sequence in which classes are searched, this is essential if plugin uses library,
  // a different version of which is used in IDEA.
  private @Nullable Class<?> tryLoadingClass(@NotNull String name, boolean resolve, @Nullable Set<ClassLoader> visited, boolean withRoot) {
    long startTime = StartUpMeasurer.getCurrentTime();
    Class<?> c = null;
    if (!mustBeLoadedByPlatform(name)) {
      c = loadClassInsideSelf(name);
    }

    if (c == null) {
      c = processResourcesInParents(name, loadClassInPluginCL, loadClassInCl, visited, null, withRoot);
    }

    if (c != null) {
      if (resolve) {
        resolveClass(c);
      }
    }

    if (StartUpMeasurer.measuringPluginStartupCosts) {
      Application app = ApplicationManager.getApplication();
      // JDK impl is not so fast as ours, use it only if no application
      boolean isEdt = app == null ? EventQueue.isDispatchThread() : app.isDispatchThread();
      (isEdt ? edtTime : backgroundTime).addAndGet(StartUpMeasurer.getCurrentTime() - startTime);
    }
    return c;
  }

  private static final Set<String> KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES = new HashSet<>(Arrays.asList(
    "kotlin.sequences.Sequence",
    "kotlin.Lazy", "kotlin.Unit",
    "kotlin.Pair", "kotlin.Triple",
    "kotlin.jvm.internal.DefaultConstructorMarker",
    "kotlin.jvm.internal.ClassBasedDeclarationContainer",
    "kotlin.properties.ReadWriteProperty",
    "kotlin.properties.ReadOnlyProperty"
  ));

  private static boolean mustBeLoadedByPlatform(String className) {
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

  private @Nullable Class<?> loadClassInsideSelf(@NotNull String name) {
    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c != null) {
        return c;
      }

      try {
        c = _findClass(name);
      }
      catch (LinkageError e) {
        throw new PluginException("While loading class " + name + ": " + e.getMessage(), e, getPluginId());
      }
      if (c != null) {
        loadedClassCounter.incrementAndGet();
      }

      return c;
    }
  }

  private static final ActionWithPluginClassLoader<URL, Void> findResourceInPluginCL = new ActionWithPluginClassLoader<URL, Void>() {
    @Override
    protected URL doExecute(String name, PluginClassLoader classloader, Void parameter) {
      return classloader.findOwnResource(name);
    }
  };

  private static final ActionWithClassloader<URL, Void> findResourceInCl = new ActionWithClassloader<URL, Void>() {
    @Override
    public URL execute(String name, ClassLoader classloader, Void parameter) {
      return classloader.getResource(name);
    }
  };

  @Override
  public URL findResource(String name) {
    URL resource = findOwnResource(name);
    if (resource != null) return resource;
    return processResourcesInParents(name, findResourceInPluginCL, findResourceInCl, null);
  }

  private @Nullable URL findOwnResource(String name) {
    return super.findResource(name);
  }

  private static final ActionWithPluginClassLoader<InputStream, Void>
    getResourceAsStreamInPluginCL = new ActionWithPluginClassLoader<InputStream, Void>() {
    @Override
    protected InputStream doExecute(String name, PluginClassLoader classloader, Void parameter) {
      return classloader.getOwnResourceAsStream(name);
    }
  };

  private static final ActionWithClassloader<InputStream, Void> getResourceAsStreamInCl = new ActionWithClassloader<InputStream, Void>() {
    @Override
    public InputStream execute(String name, ClassLoader classloader, Void parameter) {
      return classloader.getResourceAsStream(name);
    }
  };

  @Override
  public InputStream getResourceAsStream(String name) {
    InputStream stream = getOwnResourceAsStream(name);
    if (stream != null) return stream;

    return processResourcesInParents(name, getResourceAsStreamInPluginCL, getResourceAsStreamInCl, null);
  }

  private @Nullable InputStream getOwnResourceAsStream(String name) {
    return super.getResourceAsStream(name);
  }

  private static final ActionWithPluginClassLoader<Void, List<Enumeration<URL>>>
    findResourcesInPluginCL = new ActionWithPluginClassLoader<Void, List<Enumeration<URL>>>() {
    @Override
    protected Void doExecute(String name,
                             PluginClassLoader classloader,
                             List<Enumeration<URL>> enumerations) {
      try {
        enumerations.add(classloader.findOwnResources(name));
      }
      catch (IOException ignore) {
      }
      return null;
    }
  };

  private static final ActionWithClassloader<Void, List<Enumeration<URL>>>
    findResourcesInCl = new ActionWithClassloader<Void, List<Enumeration<URL>>>() {
    @Override
    public Void execute(String name, ClassLoader classloader, List<Enumeration<URL>> enumerations) {
      try {
        enumerations.add(classloader.getResources(name));
      }
      catch (IOException ignore) {
      }
      return null;
    }
  };

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    List<Enumeration<URL>> resources = new ArrayList<>();
    resources.add(findOwnResources(name));
    processResourcesInParents(name, findResourcesInPluginCL, findResourcesInCl, resources);
    return new DeepEnumeration(resources);
  }

  private Enumeration<URL> findOwnResources(String name) throws IOException {
    return super.findResources(name);
  }

  @SuppressWarnings("UnusedDeclaration")
  public void addLibDirectories(@NotNull Collection<String> libDirectories) {
    myLibDirectories.addAll(libDirectories);
  }

  @Override
  protected String findLibrary(String libName) {
    if (!myLibDirectories.isEmpty()) {
      String libFileName = System.mapLibraryName(libName);
      ListIterator<String> i = myLibDirectories.listIterator(myLibDirectories.size());
      while (i.hasPrevious()) {
        File libFile = new File(i.previous(), libFileName);
        if (libFile.exists()) {
          return libFile.getAbsolutePath();
        }
      }
    }
    return null;
  }

  public @NotNull PluginId getPluginId() {
    return pluginId;
  }

  public @NotNull IdeaPluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @Override
  public String toString() {
    return "PluginClassLoader[" + myPluginDescriptor + "] " + super.toString();
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
  public @NotNull List<ClassLoader> _getParents() {
    //noinspection SSBasedInspection
    return Collections.unmodifiableList(Arrays.asList(myParents));
  }

  @ApiStatus.Internal
  public void attachParent(@NotNull ClassLoader classLoader) {
    myParents = ArrayUtil.append(myParents, classLoader);
  }

  @ApiStatus.Internal
  public boolean detachParent(@NotNull ClassLoader classLoader) {
    int oldSize = myParents.length;
    myParents = ArrayUtil.remove(myParents, classLoader);
    return myParents.length == oldSize - 1;
  }

  @Override
  protected ProtectionDomain getProtectionDomain(URL url) {
    // avoid capturing reference to classloader in AccessControlContext
    return new ProtectionDomain(new CodeSource(url, (Certificate[])null), null);
  }
}
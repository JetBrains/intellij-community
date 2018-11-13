// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.cl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class PluginClassLoader extends UrlClassLoader {
  static { 
    if (registerAsParallelCapable()) markParallelCapable(PluginClassLoader.class); 
  }
  private final ClassLoader[] myParents;
  private final PluginId myPluginId;
  private final String myPluginVersion;
  private final List<String> myLibDirectories;

  public PluginClassLoader(@NotNull List<URL> urls,
                           @NotNull ClassLoader[] parents,
                           PluginId pluginId,
                           String version,
                           File pluginRoot) {
    super(build().urls(urls).allowLock().useCache());
    myParents = parents;
    myPluginId = pluginId;
    myPluginVersion = version;
    myLibDirectories = ContainerUtil.newSmartList();
    File libDir = new File(pluginRoot, "lib");
    if (libDir.exists()) {
      myLibDirectories.add(libDir.getAbsolutePath());
    }
  }

  @Override
  public Class loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
    Class c = tryLoadingClass(name, resolve, null);
    if (c == null) {
      throw new ClassNotFoundException(name + " " + this);
    }
    return c;
  }

  private interface ActionWithClassloader<T, T2> {
    T execute(String name, ClassLoader classloader, T2 extraParameter);
  }

  private abstract static class ActionWithPluginClassLoader<T, T2> {
    T execute(String name, PluginClassLoader classloader, Set<ClassLoader> visited,
              ActionWithPluginClassLoader<T, T2> action, ActionWithClassloader<T, T2> action2, T2 extraParameter) {
      T resource = doExecute(name, classloader, extraParameter);
      if (resource != null) return resource;
      return classloader.processResourcesInParents(name, action, action2, visited, extraParameter);
    }

    protected abstract T doExecute(String name, PluginClassLoader classloader, T2 extraParameter);
  }

  @Nullable
  private <T, T2> T processResourcesInParents(String name,
                                              ActionWithPluginClassLoader<T, T2> actionWithPluginClassLoader,
                                              ActionWithClassloader<T, T2> actionWithClassloader,
                                              Set<ClassLoader> visited, T2 extraParameter) {
    for (ClassLoader parent : myParents) {
      if (visited == null) visited = ContainerUtilRt.newHashSet(this);
      if (!visited.add(parent)) {
        continue;
      }

      if (parent instanceof PluginClassLoader) {
        T resource = actionWithPluginClassLoader.execute(name, (PluginClassLoader)parent, visited, actionWithPluginClassLoader,
                                                         actionWithClassloader, extraParameter);
        if (resource != null) {
          return resource;
        }
        continue;
      }

      T resource = actionWithClassloader.execute(name, parent, extraParameter);
      if (resource != null) return resource;
    }

    return null;
  }

  private static final ActionWithPluginClassLoader<Class, Void> loadClassInPluginCL = new ActionWithPluginClassLoader<Class, Void>() {
    @Override
    Class execute(String name,
                  PluginClassLoader classloader,
                  Set<ClassLoader> visited,
                  ActionWithPluginClassLoader<Class, Void> action,
                  ActionWithClassloader<Class, Void> action2,
                  Void extraParameter) {
      return classloader.tryLoadingClass(name, false, visited);
    }

    @Override
    protected Class doExecute(String name, PluginClassLoader classloader, Void extraParameter) {
      return null;
    }
  };

  private static final ActionWithClassloader<Class, Void> loadClassInCl = new ActionWithClassloader<Class, Void>() {
    @Override
    public Class execute(String name, ClassLoader classloader, Void extraParameter) {
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
  @Nullable
  private Class tryLoadingClass(@NotNull String name, boolean resolve, @Nullable Set<ClassLoader> visited) {
    Class c = null;
    if (!mustBeLoadedByPlatform(name)) {
      c = loadClassInsideSelf(name);
    }

    if (c == null) {
      c = processResourcesInParents(name, loadClassInPluginCL, loadClassInCl, visited, null);
    }

    if (c != null) {
      if (resolve) {
        resolveClass(c);
      }
      return c;
    }

    return null;
  }

  private static final Set<String> KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES = ContainerUtil.set(
    "kotlin.sequences.Sequence",
    "kotlin.Unit",
    "kotlin.Pair",
    "kotlin.Triple",
    "kotlin.jvm.internal.DefaultConstructorMarker",
    "kotlin.properties.ReadWriteProperty",
    "kotlin.properties.ReadOnlyProperty"
  );

  private static boolean mustBeLoadedByPlatform(String className) {
    if (className.startsWith("java.") || className.startsWith("javax.")) return true;
    //some commonly used classes from kotlin-runtime must be loaded by the platform classloader. Otherwise if a plugin bundles its own version
    // of kotlin-runtime.jar it won't be possible to call platform's methods with these types in signatures from such a plugin.
    //We assume that these classes don't change between Kotlin versions so it's safe to always load them from platform's kotlin-runtime.
    return className.startsWith("kotlin.") && (className.startsWith("kotlin.jvm.functions.") ||
                                               className.startsWith("kotlin.reflect.") ||
                                               className.startsWith("kotlin.jvm.internal.") ||
                                               KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES.contains(className));
  }

  @Nullable
  private Class loadClassInsideSelf(@NotNull String name) {
    synchronized (getClassLoadingLock(name)) {
      Class c = findLoadedClass(name);
      if (c != null) {
        return c;
      }

      try {
        c = _findClass(name);
      }
      catch (IncompatibleClassChangeError | UnsupportedClassVersionError e) {
        throw new PluginException("While loading class " + name + ": " + e.getMessage(), e, myPluginId);
      }
      if (c != null) {
        PluginManagerCore.addPluginClass(myPluginId);
      }

      return c;
    }
  }
  
  private static final ActionWithPluginClassLoader<URL, Void> findResourceInPluginCL = new ActionWithPluginClassLoader<URL, Void>() {
    @Override
    protected URL doExecute(String name, PluginClassLoader classloader, Void extraParameter) {
      return classloader.findOwnResource(name);
    }
  };
  
  private static final ActionWithClassloader<URL, Void> findResourceInCl = new ActionWithClassloader<URL, Void>() {
    @Override
    public URL execute(String name, ClassLoader classloader, Void extraParameter) {
      return classloader.getResource(name);
    }
  };
  
  @Override
  public URL findResource(String name) {
    URL resource = findOwnResource(name);
    if (resource != null) return resource;

    return processResourcesInParents(name, findResourceInPluginCL, findResourceInCl, null, null);
  }

  @Nullable
  private URL findOwnResource(String name) {
    URL resource = super.findResource(name);
    if (resource != null) return resource;
    return null;
  }

  private static final ActionWithPluginClassLoader<InputStream, Void>
    getResourceAsStreamInPluginCL = new ActionWithPluginClassLoader<InputStream, Void>() {
    @Override
    protected InputStream doExecute(String name, PluginClassLoader classloader, Void extraParameter) {
      return classloader.getOwnResourceAsStream(name);
    }
  };

  private static final ActionWithClassloader<InputStream, Void> getResourceAsStreamInCl = new ActionWithClassloader<InputStream, Void>() {
    @Override
    public InputStream execute(String name, ClassLoader classloader, Void extraParameter) {
      return classloader.getResourceAsStream(name);
    }
  };
  
  @Override
  public InputStream getResourceAsStream(String name) {
    InputStream stream = getOwnResourceAsStream(name);
    if (stream != null) return stream;

    return processResourcesInParents(name, getResourceAsStreamInPluginCL, getResourceAsStreamInCl, null, null);
  }

  @Nullable
  private InputStream getOwnResourceAsStream(String name) {
    InputStream stream = super.getResourceAsStream(name);
    if (stream != null) return stream;
    return null;
  }

  private static final ActionWithPluginClassLoader<Void, List<Enumeration<URL>>>
    findResourcesInPluginCL = new ActionWithPluginClassLoader<Void, List<Enumeration<URL>>>() {
    @Override
    protected Void doExecute(String name,
                             PluginClassLoader classloader,
                             List<Enumeration<URL>> extraParameter) {
      try {
        extraParameter.add(classloader.findOwnResources(name));
      } catch (IOException ignore) {}
      return null;
    }
  };

  private static final ActionWithClassloader<Void, List<Enumeration<URL>>>
    findResourcesInCl = new ActionWithClassloader<Void, List<Enumeration<URL>>>() {
    @Override
    public Void execute(String name, ClassLoader classloader, List<Enumeration<URL>> extraParameter) {
      try {
        extraParameter.add(classloader.getResources(name));
      } catch (IOException ignore) {}
      return null;
    }
  };
  
  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    @SuppressWarnings("unchecked") List<Enumeration<URL>> resources = new ArrayList<>();
    resources.add(findOwnResources(name));
    processResourcesInParents(name, findResourcesInPluginCL, findResourcesInCl, null, resources);
    return new DeepEnumeration(resources.toArray(new Enumeration[resources.size()]));
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

  public PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  public String toString() {
    return "PluginClassLoader[" + myPluginId + ", " + myPluginVersion + "] " + super.toString();
  }

  private static class DeepEnumeration implements Enumeration<URL> {
    private final Enumeration<URL>[] myEnumerations;
    private int myIndex;

    DeepEnumeration(Enumeration<URL>[] enumerations) {
      myEnumerations = enumerations;
    }

    @Override
    public boolean hasMoreElements() {
      while (myIndex < myEnumerations.length) {
        Enumeration<URL> e = myEnumerations[myIndex];
        if (e != null && e.hasMoreElements()) return true;
        myIndex++;
      }
      return false;
    }

    @Override
    public URL nextElement() {
      if (!hasMoreElements()) {
        throw new NoSuchElementException();
      }
      return myEnumerations[myIndex].nextElement();
    }
  }
}
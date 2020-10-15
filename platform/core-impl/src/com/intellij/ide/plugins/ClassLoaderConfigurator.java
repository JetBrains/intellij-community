// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
final class ClassLoaderConfigurator {
  private static final ClassLoader[] EMPTY_CLASS_LOADER_ARRAY = new ClassLoader[0];
  private static final boolean SEPARATE_CLASSLOADER_FOR_SUB = Boolean.parseBoolean(System.getProperty("idea.classloader.per.descriptor", "false"));

  // weird flag with unknown purpose
  private final boolean usePluginClassLoader;
  private final ClassLoader coreLoader;
  private final Map<PluginId, IdeaPluginDescriptorImpl> idMap;
  private final Map<String, String[]> additionalLayoutMap;

  private Optional<IdeaPluginDescriptorImpl> javaDep;

  // temporary list to produce arrays (avoid allocation for each plugin)
  private final ArrayList<ClassLoader> loaders = new ArrayList<>();
  private final boolean hasAllModules;

  private final UrlClassLoader.Builder urlClassLoaderBuilder;

  ClassLoaderConfigurator(boolean usePluginClassLoader,
                          @NotNull ClassLoader coreLoader,
                          @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                          @NotNull Map<String, String[]> additionalLayoutMap) {
    this.usePluginClassLoader = usePluginClassLoader;
    this.coreLoader = coreLoader;
    this.idMap = idMap;
    this.additionalLayoutMap = additionalLayoutMap;

    hasAllModules = idMap.containsKey(PluginManagerCore.ALL_MODULES_MARKER);
    urlClassLoaderBuilder = UrlClassLoader.build().allowLock().useCache().urlsInterned();
  }

  @SuppressWarnings("RedundantSuppression")
  private static @NotNull Logger getLogger() {
    // do not use class reference here
    //noinspection SSBasedInspection
    return Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
  }

  void configure(@NotNull IdeaPluginDescriptorImpl mainDependent) {
    if (mainDependent.getPluginId() == PluginManagerCore.CORE_ID || mainDependent.isUseCoreClassLoader()) {
      setPluginClassLoaderForMainAndSubPlugins(mainDependent, coreLoader);
      return;
    }
    else if (!usePluginClassLoader) {
      setPluginClassLoaderForMainAndSubPlugins(mainDependent, null);
    }

    loaders.clear();

    // first, set class loader for main descriptor
    if (hasAllModules) {
      IdeaPluginDescriptorImpl implicitDependency = PluginManagerCore.getImplicitDependency(mainDependent, () -> {
        // first, set class loader for main descriptor
        if (javaDep == null) {
          javaDep = Optional.ofNullable(idMap.get(PluginManagerCore.JAVA_PLUGIN_ID));
        }
        return javaDep.orElse(null);
      });

      if (implicitDependency != null) {
        addLoaderOrLogError(mainDependent, implicitDependency, loaders);
      }
    }

    List<Path> classPath = mainDependent.jarFiles;
    if (classPath == null) {
      classPath = mainDependent.collectClassPath(additionalLayoutMap);
    }
    else {
      mainDependent.jarFiles = null;
    }

    List<URL> urls = new ArrayList<>(classPath.size());
    for (Path pathElement : classPath) {
      urls.add(localFileToUrl(pathElement, mainDependent));
    }

    urlClassLoaderBuilder.urls(urls);

    List<PluginDependency> pluginDependencies = mainDependent.pluginDependencies;
    if (pluginDependencies == null) {
      loaders.add(coreLoader);
      mainDependent.setClassLoader(createPluginClassLoader(loaders.toArray(EMPTY_CLASS_LOADER_ARRAY), classPath, mainDependent));
      return;
    }

    // no need to process dependencies recursively because dependency will use own classloader
    // (that in turn will delegate class searching to parent class loader if needed)
    for (PluginDependency dependency : pluginDependencies) {
      if (dependency.isDisabledOrBroken || (SEPARATE_CLASSLOADER_FOR_SUB && dependency.subDescriptor != null)) {
        continue;
      }

      IdeaPluginDescriptorImpl dependencyDescriptor = idMap.get(dependency.id);
      if (dependencyDescriptor != null) {
        addLoaderOrLogError(mainDependent, dependencyDescriptor, loaders);
      }
    }

    // do not add core loader if there is some dependency - will be added to some dependency
    ClassLoader[] parentLoaders = loaders.isEmpty() ? new ClassLoader[]{coreLoader} : loaders.toArray(EMPTY_CLASS_LOADER_ARRAY);
    ClassLoader mainDependentClassLoader = createPluginClassLoader(parentLoaders, classPath, mainDependent);

    // second, set class loaders for sub descriptors
    if (SEPARATE_CLASSLOADER_FOR_SUB) {
      mainDependent.setClassLoader(mainDependentClassLoader);
      configureSubPlugins(mainDependentClassLoader, pluginDependencies, urlClassLoaderBuilder);
    }
    else {
      setPluginClassLoaderForMainAndSubPlugins(mainDependent, mainDependentClassLoader);
    }
  }

  private void configureSubPlugins(@NotNull ClassLoader mainDependentClassLoader,
                                   @NotNull List<PluginDependency> pluginDependencies,
                                   @NotNull UrlClassLoader.Builder urlClassLoaderBuilder) {
    for (PluginDependency dependencyInfo : pluginDependencies) {
      IdeaPluginDescriptorImpl dependent = dependencyInfo.isDisabledOrBroken ? null : dependencyInfo.subDescriptor;
      if (dependent == null) {
        continue;
      }

      assert !dependent.isUseIdeaClassLoader();

      loaders.clear();
      // add main descriptor classloader as parent
      loaders.add(mainDependentClassLoader);

      IdeaPluginDescriptorImpl dependency = idMap.get(dependencyInfo.id);
      if (dependency != null) {
        addLoaderOrLogError(dependent, dependency, loaders);
      }

      dependent.setClassLoader(new PluginClassLoader(urlClassLoaderBuilder, loaders.toArray(EMPTY_CLASS_LOADER_ARRAY), dependent, dependent.getPluginPath()));
    }
  }

  private @NotNull ClassLoader createPluginClassLoader(@NotNull ClassLoader @NotNull [] parentLoaders,
                                                       @NotNull List<Path> classPath,
                                                       @NotNull IdeaPluginDescriptorImpl descriptor) {
    if (descriptor.isUseIdeaClassLoader()) {
      getLogger().warn(descriptor.getPluginId() + " uses deprecated `use-idea-classloader` attribute");
      ClassLoader loader = PluginManagerCore.class.getClassLoader();
      try {
        Class<?> loaderClass = loader.getClass();
        if (loaderClass.getName().endsWith(".BootstrapClassLoaderUtil$TransformingLoader")) {
          loaderClass = loaderClass.getSuperclass();
        }

        // `UrlClassLoader#addURL` can't be invoked directly, because the core classloader is created at bootstrap in a "lost" branch
        MethodHandle addURL = MethodHandles.lookup().findVirtual(loaderClass, "addURL", MethodType.methodType(void.class, URL.class));
        for (Path pathElement : classPath) {
          addURL.invoke(loader, localFileToUrl(pathElement, descriptor));
        }
        return loader;
      }
      catch (Throwable t) {
        throw new IllegalStateException("An unexpected core classloader: " + loader.getClass(), t);
      }
    }
    else {
      PluginClassLoader loader = new PluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor, descriptor.getPluginPath());
      if (usePluginClassLoader) {
        loader.setCoreLoader(coreLoader);
      }
      return loader;
    }
  }

  private static void addLoaderOrLogError(@NotNull IdeaPluginDescriptorImpl dependent,
                                          @NotNull IdeaPluginDescriptorImpl dependency,
                                          @NotNull List<ClassLoader> loaders) {
    ClassLoader loader = dependency.getClassLoader();
    if (loader == null) {
      getLogger().error(PluginLoadingError.formatErrorMessage(dependent,
                                                              "requires missing class loader for '" + dependency.getName() + "'"));
    }
    else {
      loaders.add(loader);
    }
  }

  private static void setPluginClassLoaderForMainAndSubPlugins(@NotNull IdeaPluginDescriptorImpl rootDescriptor, @Nullable ClassLoader classLoader) {
    rootDescriptor.setClassLoader(classLoader);
    for (PluginDependency dependency : rootDescriptor.getPluginDependencies()) {
      if (dependency.subDescriptor != null) {
        dependency.subDescriptor.setClassLoader(classLoader);
      }
    }
  }

  private static @NotNull URL localFileToUrl(@NotNull Path file, @NotNull IdeaPluginDescriptor descriptor) {
    try {
      // it is important not to have traversal elements in classpath
      return file.normalize().toUri().toURL();
    }
    catch (MalformedURLException e) {
      throw new PluginException("Corrupted path element: `" + file + '`', e, descriptor.getPluginId());
    }
  }
}

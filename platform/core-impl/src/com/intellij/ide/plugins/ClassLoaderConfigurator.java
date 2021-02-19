// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.lang.ClassPath;
import com.intellij.util.lang.UrlClassLoader;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
@ApiStatus.Internal
final class ClassLoaderConfigurator {
  // this list doesn't duplicate of PluginXmlFactory.CLASS_NAMES - interface related must be not here
  private static final @NonNls Set<String> IMPL_CLASS_NAMES = new ReferenceOpenHashSet<>(Arrays.asList(
    "implementation", "implementationClass", "builderClass",
    "serviceImplementation", "class", "className",
    "instance", "implementation-class"));

  // grab classes from platform loader only if nothing is found in any of plugin dependencies
  private final boolean usePluginClassLoader;
  private final ClassLoader coreLoader;
  final Map<PluginId, IdeaPluginDescriptorImpl> idMap;
  private final Map<String, String[]> additionalLayoutMap;

  private Optional<IdeaPluginDescriptorImpl> javaDep;

  // temporary set to produce arrays (avoid allocation for each plugin)
  // set to remove duplicated classloaders
  private final Set<ClassLoader> loaders = new LinkedHashSet<>();
  // temporary list to produce arrays (avoid allocation for each plugin)
  private final List<String> packagePrefixes = new ArrayList<>();

  private final boolean hasAllModules;

  private final UrlClassLoader.Builder urlClassLoaderBuilder;

  // todo for dynamic reload this guard doesn't contain all used plugin prefixes
  private final Set<String> pluginPackagePrefixUniqueGuard = new HashSet<>();

  private final ClassPath.ResourceFileFactory resourceFileFactory;

  ClassLoaderConfigurator(boolean usePluginClassLoader,
                          @NotNull ClassLoader coreLoader,
                          @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap,
                          @NotNull Map<String, String[]> additionalLayoutMap) {
    this.usePluginClassLoader = usePluginClassLoader;
    this.coreLoader = coreLoader;
    this.idMap = idMap;
    this.additionalLayoutMap = additionalLayoutMap;

    hasAllModules = idMap.containsKey(PluginManagerCore.ALL_MODULES_MARKER);
    urlClassLoaderBuilder = UrlClassLoader.build().useCache();

    ClassPath.ResourceFileFactory resourceFileFactory;
    try {
      resourceFileFactory = (ClassPath.ResourceFileFactory)MethodHandles.lookup()
        .findStatic(coreLoader.loadClass("com.intellij.util.lang.PathClassLoader"), "getResourceFileFactory",
                    MethodType.methodType(ClassPath.ResourceFileFactory.class))
        .invokeExact();
    }
    catch (ClassNotFoundException ignore) {
      resourceFileFactory = null;
    }
    catch (Throwable e) {
      getLogger().error(e);
      resourceFileFactory = null;
    }
    this.resourceFileFactory = resourceFileFactory;
  }

  @SuppressWarnings("RedundantSuppression")
  private static @NotNull Logger getLogger() {
    // do not use class reference here
    //noinspection SSBasedInspection
    return Logger.getInstance("#com.intellij.ide.plugins.PluginManager");
  }

  void configureDependenciesIfNeeded(@NotNull Map<IdeaPluginDescriptorImpl, @NotNull List<IdeaPluginDescriptorImpl>> mainToSub, @NotNull IdeaPluginDescriptorImpl dependencyPlugin) {
    for (Map.Entry<IdeaPluginDescriptorImpl, @NotNull List<IdeaPluginDescriptorImpl>> entry : mainToSub.entrySet()) {
      IdeaPluginDescriptorImpl mainDependent = entry.getKey();
      PluginClassLoader mainDependentClassLoader = (PluginClassLoader)Objects.requireNonNull(mainDependent.getClassLoader());

      if (isClassloaderPerDescriptorEnabled(mainDependent)) {
        for (PluginDependency dependency : Objects.requireNonNull(mainDependent.pluginDependencies)) {
          urlClassLoaderBuilder.files(mainDependentClassLoader.getFiles());
          for (IdeaPluginDescriptorImpl subDescriptor : entry.getValue()) {
            if (subDescriptor == dependency.subDescriptor) {
              configureSubPlugin(dependency, mainDependentClassLoader, mainDependent);
              break;
            }
          }
        }
      }
      else {
        mainDependentClassLoader.attachParent(Objects.requireNonNull(dependencyPlugin.getClassLoader()));
        for (IdeaPluginDescriptorImpl subDescriptor : entry.getValue()) {
          subDescriptor.setClassLoader(mainDependentClassLoader);
        }
      }
    }

    loaders.clear();
    urlClassLoaderBuilder.files(Collections.emptyList());
  }

  void configure(@NotNull IdeaPluginDescriptorImpl mainDependent) {
    String pluginPackagePrefix = mainDependent.packagePrefix;
    if (pluginPackagePrefix != null && !pluginPackagePrefixUniqueGuard.add(pluginPackagePrefix)) {
      throw new PluginException("Package prefix " + pluginPackagePrefix + " is already used", mainDependent.getPluginId());
    }

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
      classPath = collectClassPath(mainDependent);
    }
    else {
      mainDependent.jarFiles = null;
    }

    urlClassLoaderBuilder.files(classPath);

    List<PluginDependency> pluginDependencies = mainDependent.pluginDependencies;
    if (pluginDependencies == null) {
      assert !mainDependent.isUseIdeaClassLoader();
      mainDependent.setClassLoader(createPluginClassLoader(mainDependent));
      return;
    }

    for (PluginDependency dependency : pluginDependencies) {
      if (!dependency.isDisabledOrBroken && (!isClassloaderPerDescriptorEnabled(mainDependent) || dependency.subDescriptor == null)) {
        addClassloaderIfDependencyEnabled(dependency.id, mainDependent);
      }
    }

    ClassLoader mainDependentClassLoader;
    if (mainDependent.isUseIdeaClassLoader()) {
      mainDependentClassLoader = configureUsingIdeaClassloader(classPath, mainDependent);
    }
    else {
      mainDependentClassLoader = createPluginClassLoader(mainDependent);
    }

    // second, set class loaders for sub descriptors
    if (usePluginClassLoader && isClassloaderPerDescriptorEnabled(mainDependent)) {
      mainDependent.setClassLoader(mainDependentClassLoader);
      for (PluginDependency dependencyInfo : pluginDependencies) {
        configureSubPlugin(dependencyInfo, mainDependentClassLoader, mainDependent);
      }
    }
    else {
      setPluginClassLoaderForMainAndSubPlugins(mainDependent, mainDependentClassLoader);
    }

    // reset to ensure that stalled data will be not reused somehow later
    loaders.clear();
    urlClassLoaderBuilder.files(Collections.emptyList());
  }

  private @NotNull PluginClassLoader createPluginClassLoader(@NotNull IdeaPluginDescriptorImpl descriptor) {
    ClassLoader[] parentLoaders = loaders.isEmpty()
                                  ? PluginClassLoader.EMPTY_CLASS_LOADER_ARRAY
                                  : loaders.toArray(PluginClassLoader.EMPTY_CLASS_LOADER_ARRAY);
    return createPluginClassLoader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory);
  }

  // static to ensure that anonymous classes will not hold ClassLoaderConfigurator
  private static @NotNull PluginClassLoader createPluginClassLoader(@NotNull ClassLoader @NotNull [] parentLoaders,
                                                                    @NotNull IdeaPluginDescriptorImpl descriptor,
                                                                    @NotNull UrlClassLoader.Builder urlClassLoaderBuilder,
                                                                    @NotNull ClassLoader coreLoader,
                                                                    @Nullable ClassPath.ResourceFileFactory resourceFileFactory) {
    String idString = descriptor.id.getIdString();
    // main plugin descriptor
    boolean isMain = descriptor.descriptorPath == null;
    if (isMain) {
      switch (idString) {
        case "com.intellij.diagram":
          // multiple packages - intellij.diagram and intellij.diagram.impl modules
          return createPluginClassLoaderWithExtraPackage(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                                         "com.intellij.diagram.");
        case "com.intellij.struts2":
          return createPluginClassLoaderWithExtraPackage(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                                         "com.intellij.lang.ognl.");
        case "com.intellij.properties":
          // todo ability to customize (cannot move due to backward compatibility)
          return createPluginClassloader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                         new PluginClassLoader.ResolveScopeManager() {
                                           @Override
                                           public boolean isDefinitelyAlienClass(String name, String packagePrefix, boolean force) {
                                             if (force) {
                                               return false;
                                             }
                                             return !name.startsWith(packagePrefix) &&
                                                    !name.startsWith("com.intellij.ultimate.PluginVerifier") &&
                                                    !name.equals("com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider");
                                           }
                                         });
      }

      if (descriptor.packagePrefix == null) {
        return new PluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor, descriptor.getPluginPath(), coreLoader, null,
                                     null, resourceFileFactory);
      }
    }
    else {
      if (!descriptor.contentDescriptor.modules.isEmpty()) {
        // see "The `content.module` element" section about content handling for a module
        return createPluginClassloader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                       createModuleContentBasedScope(descriptor));
      }
      else if (descriptor.packagePrefix != null) {
        return createPluginClassloader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                       new PluginClassLoader.ResolveScopeManager() {
                                         @Override
                                         public boolean isDefinitelyAlienClass(String name, String packagePrefix, boolean force) {
                                           // force flag is ignored for module - e.g. RailsViewLineMarkerProvider is referenced
                                           // as extension implementation in several modules
                                           return !name.startsWith(packagePrefix) &&
                                                  !name.startsWith("com.intellij.ultimate.PluginVerifier");
                                         }
                                       });
      }
    }

    return createPluginClassloader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                   createPluginDependencyAndContentBasedScope(descriptor));
  }

  private static @NotNull PluginClassLoader createPluginClassloader(@NotNull ClassLoader @NotNull [] parentLoaders,
                                                                    @NotNull IdeaPluginDescriptorImpl descriptor,
                                                                    @NotNull UrlClassLoader.Builder urlClassLoaderBuilder,
                                                                    @NotNull ClassLoader coreLoader,
                                                                    @Nullable ClassPath.ResourceFileFactory resourceFileFactory,
                                                                    @Nullable PluginClassLoader.ResolveScopeManager resolveScopeManager) {
    return new PluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor, descriptor.getPluginPath(), coreLoader,
                                 resolveScopeManager, descriptor.packagePrefix, resourceFileFactory);
  }

  private static @NotNull PluginClassLoader createPluginClassLoaderWithExtraPackage(@NotNull ClassLoader @NotNull [] parentLoaders,
                                                                                    @NotNull IdeaPluginDescriptorImpl descriptor,
                                                                                    @NotNull UrlClassLoader.Builder urlClassLoaderBuilder,
                                                                                    @NotNull ClassLoader coreLoader,
                                                                                    @Nullable ClassPath.ResourceFileFactory resourceFileFactory,
                                                                                    @NotNull String customPackage) {
    return createPluginClassloader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                   new PluginClassLoader.ResolveScopeManager() {
                                     @Override
                                     public boolean isDefinitelyAlienClass(String name, String packagePrefix, boolean force) {
                                       if (force) {
                                         return false;
                                       }
                                       return !name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier") &&
                                              !name.startsWith(customPackage);
                                     }
                                   });
  }

  // package of module is not taken in account to support resolving of module libraries -
  // instead, only classes from plugin's modules (content or dependencies) are excluded.
  private static @NotNull PluginClassLoader.ResolveScopeManager createPluginDependencyAndContentBasedScope(@NotNull IdeaPluginDescriptorImpl descriptor) {
    List<String> contentPackagePrefixes = getContentPackagePrefixes(descriptor);
    List<String> dependencyPackagePrefixes = getDependencyPackagePrefixes(descriptor);
    String pluginId = descriptor.getPluginId().getIdString();
    return (name, __, force) -> {
      if (force) {
        return false;
      }

      for (String prefix : contentPackagePrefixes) {
        if (name.startsWith(prefix)) {
          getLogger().error("Class " + name + " must be not requested from main classloader of " + pluginId + " plugin");
          return true;
        }
      }
      for (String prefix : dependencyPackagePrefixes) {
        if (name.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    };
  }

  private static @NotNull List<String> getContentPackagePrefixes(@NotNull IdeaPluginDescriptorImpl descriptor) {
    List<String> result = null;
    for (PluginContentDescriptor.ModuleItem item : descriptor.contentDescriptor.modules) {
      if (item.isInjected) {
        continue;
      }

      String packagePrefix = item.packageName;
      if (packagePrefix != null) {
        if (result == null) {
          result = new ArrayList<>(descriptor.contentDescriptor.modules.size());
        }
        result.add(packagePrefix + '.');
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private static @NotNull List<String> getDependencyPackagePrefixes(@NotNull IdeaPluginDescriptorImpl descriptor) {
    if (descriptor.dependencyDescriptor.modules.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>(descriptor.dependencyDescriptor.modules.size());
    for (ModuleDependenciesDescriptor.ModuleItem item : descriptor.dependencyDescriptor.modules) {
      String packagePrefix = item.packageName;
      // intellij.platform.commercial.verifier is injected
      if (packagePrefix != null && !item.name.equals("intellij.platform.commercial.verifier")) {
        result.add(packagePrefix + '.');
      }
    }
    return result;
  }

  private static @NotNull PluginClassLoader.ResolveScopeManager createModuleContentBasedScope(@NotNull IdeaPluginDescriptorImpl descriptor) {
    List<String> packagePrefixes = new ArrayList<>(descriptor.contentDescriptor.modules.size());
    for (PluginContentDescriptor.ModuleItem item : descriptor.contentDescriptor.modules) {
      String packagePrefix = item.packageName;
      if (packagePrefix != null) {
        packagePrefixes.add(packagePrefix + '.');
      }
    }
    // force flag is ignored for module - e.g. RailsViewLineMarkerProvider is referenced as extension implementation in several modules
    return (name, packagePrefix, force) -> {
      if (name.startsWith(packagePrefix) || name.startsWith("com.intellij.ultimate.PluginVerifier")) {
        return false;
      }

      // for a module, the referenced module doesn't have own classloader and is added directly to classpath,
      // so, if name doesn't pass standard package prefix filter,
      // check that it is not in content - if in content, then it means that class is not alien
      for (String prefix : packagePrefixes) {
        if (name.startsWith(prefix)) {
          return false;
        }
      }
      return true;
    };
  }

  private static boolean isClassloaderPerDescriptorEnabled(@NotNull IdeaPluginDescriptorImpl descriptor) {
    return ClassLoaderConfigurationData.isClassloaderPerDescriptorEnabled(descriptor.id, descriptor.packagePrefix);
  }

  private void configureSubPlugin(@NotNull PluginDependency dependencyInfo,
                                  @NotNull ClassLoader mainDependentClassLoader,
                                  @NotNull IdeaPluginDescriptorImpl parentDescriptor) {
    IdeaPluginDescriptorImpl dependent = dependencyInfo.isDisabledOrBroken ? null : dependencyInfo.subDescriptor;
    if (dependent == null) {
      return;
    }

    assert !dependent.isUseIdeaClassLoader();
    String pluginPackagePrefix = dependent.packagePrefix;
    if (pluginPackagePrefix == null) {
      if (parentDescriptor.packagePrefix != null) {
        throw new PluginException("Sub descriptor must specify package if it is specified for main plugin descriptor " +
                                  "(descriptorFile=" + dependent.descriptorPath + ")", parentDescriptor.id);
      }
    }
    else {
      if (pluginPackagePrefix.equals(parentDescriptor.packagePrefix)) {
        throw new PluginException("Sub descriptor must not specify the same package as main plugin descriptor", parentDescriptor.id);
      }
      if (parentDescriptor.packagePrefix == null) {
        String parentId = parentDescriptor.id.getIdString();
        if (!(parentId.equals("Docker") ||
              parentId.equals("org.jetbrains.plugins.ruby") ||
              parentId.equals("JavaScript"))) {
          throw new PluginException("Sub descriptor must not specify package if one is not specified for main plugin descriptor",
                                    parentDescriptor.id);
        }
      }

      if (!pluginPackagePrefixUniqueGuard.add(pluginPackagePrefix)) {
        throw new PluginException("Package prefix " + pluginPackagePrefix + " is already used", parentDescriptor.id);
      }
    }

    IdeaPluginDescriptorImpl dependency = idMap.get(dependencyInfo.id);
    if (dependency == null || !dependency.isEnabled()) {
      return;
    }

    if (pluginPackagePrefix == null) {
      packagePrefixes.clear();
      collectPackagePrefixes(dependent, packagePrefixes);
      // no package prefixes if only bean extension points are configured
      if (packagePrefixes.isEmpty()) {
        getLogger().debug("Optional descriptor " + dependencyInfo + " contains only bean extension points or light services");
      }
    }

    loaders.clear();

    // must be before main descriptor classloader
    // only first level is supported - N level is not supported for a new model (several requirements maybe specified instead)
    if (parentDescriptor.descriptorPath == null) {
      addSiblingClassloaderIfNeeded(dependent, parentDescriptor);
    }

    // add main descriptor classloader as parent
    loaders.add(mainDependentClassLoader);
    addLoaderOrLogError(dependent, dependency, loaders);

    List<PluginDependency> pluginDependencies = dependent.pluginDependencies;

    // add config-less dependencies to classloader parents
    if (pluginDependencies != null) {
      for (PluginDependency subDependency : pluginDependencies) {
        if (!subDependency.isDisabledOrBroken && subDependency.subDescriptor == null) {
          addClassloaderIfDependencyEnabled(subDependency.id, dependent);
        }
      }
    }

    PluginClassLoader subClassloader;
    if (pluginPackagePrefix == null) {
      subClassloader = new SubPluginClassLoader(dependent,
                                                urlClassLoaderBuilder,
                                                loaders.toArray(PluginClassLoader.EMPTY_CLASS_LOADER_ARRAY),
                                                packagePrefixes.toArray(ArrayUtilRt.EMPTY_STRING_ARRAY),
                                                coreLoader, resourceFileFactory);
    }
    else {
      subClassloader = createPluginClassLoader(dependent);
    }
    dependent.setClassLoader(subClassloader);

    if (pluginDependencies != null) {
      for (PluginDependency subDependency : pluginDependencies) {
        configureSubPlugin(subDependency, subClassloader, dependent);
      }
    }
  }

  private void addSiblingClassloaderIfNeeded(@NotNull IdeaPluginDescriptorImpl dependent,
                                             @NotNull IdeaPluginDescriptorImpl parentDescriptor) {
    if (!ClassLoaderConfigurationData.SEPARATE_CLASSLOADER_FOR_SUB) {
      return;
    }

    for (ModuleDependenciesDescriptor.ModuleItem dependentModuleDependency : dependent.dependencyDescriptor.modules) {
      PluginContentDescriptor.ModuleItem dependencyContentModule =
        parentDescriptor.contentDescriptor.findModuleByName(dependentModuleDependency.name);
      if (dependencyContentModule == null) {
        // todo what about dependency on a module that contained in another plugin?
        throw new PluginException("Main descriptor " + parentDescriptor + " must list module in content if it is specified as dependency in sub descriptor " +
                                  "(descriptorFile=" + dependent.descriptorPath + ")", parentDescriptor.id);
      }

      for (PluginDependency dependencyPluginDependency : Objects.requireNonNull(parentDescriptor.pluginDependencies)) {
        if (!dependencyPluginDependency.isDisabledOrBroken &&
            dependencyPluginDependency.subDescriptor != null &&
            dependentModuleDependency.packageName.equals(dependencyPluginDependency.subDescriptor.packagePrefix)) {
          ClassLoader classLoader = dependencyPluginDependency.subDescriptor.getClassLoader();
          if (classLoader == null) {
            throw new PluginException("Classloader is null for sibling. " +
                                      "Please ensure that content entry in the main plugin specifies module with package `" +
                                      dependentModuleDependency.packageName +
                                      "` before module with package `" + dependent.packagePrefix + "`" +
                                      "(descriptorFile=" + dependent.descriptorPath + ")", parentDescriptor.id);
          }
          loaders.add(classLoader);
        }
      }
    }
  }

  private void addClassloaderIfDependencyEnabled(@NotNull PluginId dependencyId, @NotNull IdeaPluginDescriptorImpl dependent) {
    IdeaPluginDescriptorImpl dependency = idMap.get(dependencyId);
    if (dependency == null) {
      return;
    }

    // must be first to ensure that it is used first to search classes (very important if main plugin descriptor doesn't have package prefix)
    // check dependencies between optional descriptors (aka modules in a new model) from different plugins
    if (ClassLoaderConfigurationData.SEPARATE_CLASSLOADER_FOR_SUB && dependency.pluginDependencies != null) {
      for (ModuleDependenciesDescriptor.ModuleItem dependentModuleDependency : dependent.dependencyDescriptor.modules) {
        if (dependency.contentDescriptor.findModuleByName(dependentModuleDependency.name) != null) {
          for (PluginDependency pluginDependency : dependency.pluginDependencies) {
            if (!pluginDependency.isDisabledOrBroken &&
                pluginDependency.subDescriptor != null &&
                dependentModuleDependency.packageName.equals(pluginDependency.subDescriptor.packagePrefix)) {
              loaders.add(pluginDependency.subDescriptor.getClassLoader());
            }
          }
          break;
        }
      }
    }

    ClassLoader loader = dependency.getClassLoader();
    if (loader == null) {
      getLogger()
        .error(PluginLoadingError.formatErrorMessage(dependent, "requires missing class loader for '" + dependency.getName() + "'"));
    }
    else if (loader != coreLoader) {
      loaders.add(loader);
    }
  }

  private static void collectPackagePrefixes(@NotNull IdeaPluginDescriptorImpl dependent, @NotNull List<String> packagePrefixes) {
    // from extensions
    dependent.getUnsortedEpNameToExtensionElements().values().forEach(elements -> {
      for (Element element : elements) {
        if (!element.hasAttributes()) {
          continue;
        }

        for (String attributeName : IMPL_CLASS_NAMES) {
          String className = element.getAttributeValue(attributeName);
          if (className != null && !className.isEmpty()) {
            addPackageByClassNameIfNeeded(className, packagePrefixes);
            break;
          }
        }
      }
    });

    // from services
    collectFromServices(dependent.appContainerDescriptor, packagePrefixes);
    collectFromServices(dependent.projectContainerDescriptor, packagePrefixes);
    collectFromServices(dependent.moduleContainerDescriptor, packagePrefixes);
  }

  private static void addPackageByClassNameIfNeeded(@NotNull String name, @NotNull List<String> packagePrefixes) {
    for (String packagePrefix : packagePrefixes) {
      if (name.startsWith(packagePrefix)) {
        return;
      }
    }

    // for classes like com.intellij.thymeleaf.lang.ThymeleafParserDefinition$SPRING_SECURITY_EXPRESSIONS
    // we must not try to load the containing package
    if (name.indexOf('$') != -1) {
      packagePrefixes.add(name);
      return;
    }

    int lastPackageDot = name.lastIndexOf('.');
    if (lastPackageDot > 0 && lastPackageDot != name.length()) {
      addPackagePrefixIfNeeded(packagePrefixes, name.substring(0, lastPackageDot + 1));
    }
  }

  private static void addPackagePrefixIfNeeded(@NotNull List<String> packagePrefixes, @NotNull String packagePrefix) {
    for (int i = 0; i < packagePrefixes.size(); i++) {
      String existingPackagePrefix = packagePrefixes.get(i);
      if (packagePrefix.startsWith(existingPackagePrefix)) {
        return;
      }
      else if (existingPackagePrefix.startsWith(packagePrefix) && existingPackagePrefix.indexOf('$') == -1) {
        packagePrefixes.set(i, packagePrefix);
        for (int j = packagePrefixes.size() - 1; j > i; j--) {
          if (packagePrefixes.get(j).startsWith(packagePrefix)) {
            packagePrefixes.remove(j);
          }
        }
        return;
      }
    }

    packagePrefixes.add(packagePrefix);
  }

  private static void collectFromServices(@NotNull ContainerDescriptor containerDescriptor, @NotNull List<String> packagePrefixes) {
    List<ServiceDescriptor> services = containerDescriptor.services;
    if (services == null) {
      return;
    }

    for (ServiceDescriptor service : services) {
      // testServiceImplementation is ignored by intention
      if (service.serviceImplementation != null) {
        addPackageByClassNameIfNeeded(service.serviceImplementation, packagePrefixes);
      }
      if (service.headlessImplementation != null) {
        addPackageByClassNameIfNeeded(service.headlessImplementation, packagePrefixes);
      }
    }
  }

  private @NotNull static ClassLoader configureUsingIdeaClassloader(@NotNull List<? extends Path> classPath, @NotNull IdeaPluginDescriptorImpl descriptor) {
    getLogger().warn(descriptor.getPluginId() + " uses deprecated `use-idea-classloader` attribute");
    ClassLoader loader = ClassLoaderConfigurator.class.getClassLoader();
    try {
      // `UrlClassLoader#addPath` can't be invoked directly, because the core classloader is created at bootstrap in a "lost" branch
      MethodHandle addFiles = MethodHandles.lookup().findVirtual(loader.getClass(), "addFiles", MethodType.methodType(void.class, List.class));
      addFiles.invoke(loader, classPath);
      return loader;
    }
    catch (Throwable e) {
      throw new IllegalStateException("An unexpected core classloader: " + loader, e);
    }
  }

  private void addLoaderOrLogError(@NotNull IdeaPluginDescriptorImpl dependent,
                                   @NotNull IdeaPluginDescriptorImpl dependency,
                                   @NotNull Collection<? super ClassLoader> loaders) {
    ClassLoader loader = dependency.getClassLoader();
    if (loader == null) {
      getLogger().error(PluginLoadingError.formatErrorMessage(dependent,
                                                              "requires missing class loader for '" + dependency.getName() + "'"));
    }
    else if (loader != coreLoader) {
      loaders.add(loader);
    }
  }

  private void setPluginClassLoaderForMainAndSubPlugins(@NotNull IdeaPluginDescriptorImpl rootDescriptor, @Nullable ClassLoader classLoader) {
    rootDescriptor.setClassLoader(classLoader);
    for (PluginDependency dependency : rootDescriptor.getPluginDependencies()) {
      if (dependency.subDescriptor != null) {
        IdeaPluginDescriptorImpl descriptor = idMap.get(dependency.id);
        if (descriptor != null && descriptor.isEnabled()) {
          setPluginClassLoaderForMainAndSubPlugins(dependency.subDescriptor, classLoader);
        }
      }
    }
  }

  private @NotNull List<Path> collectClassPath(@NotNull IdeaPluginDescriptorImpl descriptor) {
    Path pluginPath = descriptor.path;
    if (!Files.isDirectory(pluginPath)) {
      return Collections.singletonList(pluginPath);
    }

    List<Path> result = new ArrayList<>();
    Path classesDir = pluginPath.resolve("classes");
    if (Files.exists(classesDir)) {
      result.add(classesDir);
    }

    if (usePluginClassLoader) {
      Path productionDirectory = pluginPath.getParent();
      if (productionDirectory.endsWith("production")) {
        result.add(pluginPath);
        String moduleName = pluginPath.getFileName().toString();
        String[] additionalPaths = additionalLayoutMap.get(moduleName);
        if (additionalPaths != null) {
          for (String path : additionalPaths) {
            result.add(productionDirectory.resolve(path));
          }
        }
      }
    }

    try (DirectoryStream<Path> childStream = Files.newDirectoryStream(pluginPath.resolve("lib"))) {
      for (Path f : childStream) {
        if (Files.isRegularFile(f)) {
          String name = f.getFileName().toString();
          if (StringUtilRt.endsWithIgnoreCase(name, ".jar") || StringUtilRt.endsWithIgnoreCase(name, ".zip")) {
            result.add(f);
          }
        }
        else {
          result.add(f);
        }
      }
    }
    catch (NoSuchFileException ignore) {
    }
    catch (IOException e) {
      PluginManagerCore.getLogger().debug(e);
    }
    return result;
  }
}

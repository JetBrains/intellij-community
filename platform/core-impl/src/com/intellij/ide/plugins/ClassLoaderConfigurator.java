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
import java.util.function.Predicate;

@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
@ApiStatus.Internal
final class ClassLoaderConfigurator {
  static final boolean SEPARATE_CLASSLOADER_FOR_SUB = Boolean.parseBoolean(System.getProperty("idea.classloader.per.descriptor", "true"));
  private static final Set<PluginId> SEPARATE_CLASSLOADER_FOR_SUB_ONLY;
  private static final Set<PluginId> SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE;

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

  static {
    String value = System.getProperty("idea.classloader.per.descriptor.only");
    if (value == null) {
       SEPARATE_CLASSLOADER_FOR_SUB_ONLY = new ReferenceOpenHashSet<>(new PluginId[]{
        PluginId.getId("org.jetbrains.plugins.ruby"),
        PluginId.getId("com.jetbrains.rubymine.customization"),
        PluginId.getId("JavaScript"),
        PluginId.getId("Docker"),
        PluginId.getId("com.intellij.diagram"),
        PluginId.getId("org.jetbrains.plugins.github")
      });
    }
    else if (value.isEmpty()) {
      SEPARATE_CLASSLOADER_FOR_SUB_ONLY = Collections.emptySet();
    }
    else {
      SEPARATE_CLASSLOADER_FOR_SUB_ONLY = new ReferenceOpenHashSet<>();
      for (String id : value.split(",")) {
        SEPARATE_CLASSLOADER_FOR_SUB_ONLY.add(PluginId.getId(id));
      }
    }

    SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE = new ReferenceOpenHashSet<>(new PluginId[]{
      PluginId.getId("org.jetbrains.kotlin"),
      PluginId.getId("com.intellij.java"),
      PluginId.getId("com.intellij.spring.batch"),
      PluginId.getId("com.intellij.spring.integration"),
      PluginId.getId("com.intellij.spring.messaging"),
      PluginId.getId("com.intellij.spring.ws"),
      PluginId.getId("com.intellij.spring.websocket"),
      PluginId.getId("com.intellij.spring.webflow"),
      PluginId.getId("com.intellij.spring.security"),
      PluginId.getId("com.intellij.spring.osgi"),
      PluginId.getId("com.intellij.spring.mvc"),
      PluginId.getId("com.intellij.spring.data"),
      PluginId.getId("com.intellij.spring.boot.run.tests"),
      PluginId.getId("com.intellij.spring.boot"),
      PluginId.getId("com.jetbrains.space"),
      PluginId.getId("com.intellij.spring"),
    });
  }

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
    if (idString.equals("com.intellij.properties")) {
      // todo ability to customize (cannot move due to backward compatibility)
      return new PluginClassLoader(urlClassLoaderBuilder, parentLoaders,
                                   descriptor, descriptor.getPluginPath(), coreLoader, descriptor.packagePrefix, resourceFileFactory) {
        @Override
        protected boolean isDefinitelyAlienClass(@NotNull String name, @NotNull String packagePrefix) {
          return super.isDefinitelyAlienClass(name, packagePrefix) &&
                 !name.equals("com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider");
        }
      };
    }
    else if (descriptor.descriptorPath == null && idString.equals("com.intellij.diagram")) {
      // multiple packages - intellij.diagram and intellij.diagram.impl modules
      return createPluginClassLoaderWithExtraPackage(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                                     "com.intellij.diagram.");
    }
    else if (descriptor.descriptorPath == null && idString.equals("com.intellij.struts2")) {
      // multiple packages - intellij.diagram and intellij.diagram.impl modules
      return createPluginClassLoaderWithExtraPackage(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                                     "com.intellij.lang.ognl.");
    }
    else if (descriptor.descriptorPath == null && idString.equals("com.intellij.kubernetes") &&
             descriptor.dependenciesDescriptor != null /* old plugin version */) {
      // kubernetes uses project libraries - not yet clear how to deal with that, that's why here we don't use default implementation
      return new FilteringPluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor,
                                            createDependencyBasedPredicate(descriptor), coreLoader, resourceFileFactory);
    }
    else if (descriptor.contentDescriptor != null && descriptor.packagePrefix == null) {
      // Assertion based on package prefix is not enough because, surprise,
      // we cannot set package prefix for some plugins for now due to number of issues.
      // For example, for docker package prefix is not and cannot be set for now.
      return new FilteringPluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor, createContentBasedPredicate(descriptor),
                                            coreLoader, resourceFileFactory);
    }
    else if (descriptor.contentDescriptor != null && descriptor.descriptorPath != null /* it is module and not a plugin */) {
      // see "The `content.module` element" section about content handling for a module
      Predicate<String> contentBasedPredicate = createContentBasedPredicate(descriptor);
      return new ContentPredicateBasedPluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor, contentBasedPredicate, coreLoader,
                                                        resourceFileFactory);
    }
    else {
      return new PluginClassLoader(urlClassLoaderBuilder, parentLoaders,
                                   descriptor, descriptor.getPluginPath(), coreLoader, descriptor.packagePrefix, resourceFileFactory);
    }
  }

  private static @NotNull PluginClassLoader createPluginClassLoaderWithExtraPackage(@NotNull ClassLoader @NotNull [] parentLoaders,
                                                                                    @NotNull IdeaPluginDescriptorImpl descriptor,
                                                                                    @NotNull UrlClassLoader.Builder urlClassLoaderBuilder,
                                                                                    @NotNull ClassLoader coreLoader,
                                                                                    @Nullable ClassPath.ResourceFileFactory resourceFileFactory,
                                                                                    @NotNull String customPackage) {
    return new PluginClassLoader(urlClassLoaderBuilder, parentLoaders,
                                 descriptor, descriptor.getPluginPath(), coreLoader, descriptor.packagePrefix, resourceFileFactory) {
      @Override
      protected boolean isDefinitelyAlienClass(@NotNull String name, @NotNull String packagePrefix) {
        return super.isDefinitelyAlienClass(name, packagePrefix) &&
               !name.startsWith(customPackage);
      }
    };
  }

  private static final class ContentPredicateBasedPluginClassLoader extends PluginClassLoader {
    private final @NotNull Predicate<? super String> contentBasedPredicate;

    private ContentPredicateBasedPluginClassLoader(@NotNull UrlClassLoader.Builder builder,
                                                   @NotNull ClassLoader @NotNull [] parentLoaders,
                                                   @NotNull IdeaPluginDescriptorImpl descriptor,
                                                   @NotNull Predicate<? super String> contentBasedPredicate,
                                                   @NotNull ClassLoader coreLoader,
                                                   @Nullable ClassPath.ResourceFileFactory resourceFileFactory) {
      super(builder, parentLoaders, descriptor, descriptor.getPluginPath(), coreLoader, descriptor.packagePrefix, resourceFileFactory);

      this.contentBasedPredicate = contentBasedPredicate;
    }

    @Override
    protected boolean isDefinitelyAlienClass(@NotNull String name, @NotNull String packagePrefix) {
      if (!super.isDefinitelyAlienClass(name, packagePrefix)) {
        return false;
      }

      // for a module, the referenced module doesn't have own classloader and is added directly to classpath,
      // so, if name doesn't pass standard package prefix filter,
      // check that it is not in content - if in content, then it means that class is not alien
      return !contentBasedPredicate.test(name);
    }
  }

  private static final class FilteringPluginClassLoader extends PluginClassLoader {
    private final @NotNull Predicate<? super String> dependencyBasedPredicate;

    private FilteringPluginClassLoader(@NotNull UrlClassLoader.Builder builder,
                                       @NotNull ClassLoader @NotNull [] parentLoaders,
                                       @NotNull IdeaPluginDescriptorImpl descriptor,
                                       @NotNull Predicate<? super String> dependencyBasedPredicate,
                                       @NotNull ClassLoader coreLoader,
                                       @Nullable ClassPath.ResourceFileFactory resourceFileFactory) {
      super(builder, parentLoaders, descriptor, descriptor.getPluginPath(), coreLoader, descriptor.packagePrefix, resourceFileFactory);

      this.dependencyBasedPredicate = dependencyBasedPredicate;
    }

    @Override
    protected boolean isDefinitelyAlienClass(@NotNull String name, @NotNull String packagePrefix) {
      return dependencyBasedPredicate.test(name);
    }
  }

  private static @NotNull Predicate<String> createDependencyBasedPredicate(@NotNull IdeaPluginDescriptorImpl descriptor) {
    List<String> packagePrefixes = new ArrayList<>(descriptor.dependenciesDescriptor.modules.size());
    for (ModuleDependenciesDescriptor.ModuleItem item : descriptor.dependenciesDescriptor.modules) {
      String packagePrefix = item.packageName;
      // intellij.platform.commercial.verifier is injected
      if (packagePrefix != null && !item.name.equals("intellij.platform.commercial.verifier")) {
        packagePrefixes.add(packagePrefix + '.');
      }
    }
    return name -> {
      for (String prefix : packagePrefixes) {
        if (name.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    };
  }

  private static @NotNull Predicate<String> createContentBasedPredicate(@NotNull IdeaPluginDescriptorImpl descriptor) {
    List<String> packagePrefixes = new ArrayList<>(descriptor.contentDescriptor.modules.size());
    for (PluginContentDescriptor.ModuleItem item : descriptor.contentDescriptor.modules) {
      String packagePrefix = item.packageName;
      if (packagePrefix != null) {
        packagePrefixes.add(packagePrefix + '.');
      }
    }
    return name -> {
      for (String prefix : packagePrefixes) {
        if (name.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    };
  }

  private static boolean isClassloaderPerDescriptorEnabled(@NotNull IdeaPluginDescriptorImpl mainDependent) {
    if (!SEPARATE_CLASSLOADER_FOR_SUB || SEPARATE_CLASSLOADER_FOR_SUB_EXCLUDE.contains(mainDependent.getPluginId())) {
      return false;
    }
    return mainDependent.packagePrefix != null ||
           SEPARATE_CLASSLOADER_FOR_SUB_ONLY.isEmpty() ||
           SEPARATE_CLASSLOADER_FOR_SUB_ONLY.contains(mainDependent.getPluginId());
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
      if (parentDescriptor.packagePrefix == null &&
          !(parentDescriptor.id.getIdString().equals("Docker") ||
            parentDescriptor.id.getIdString().equals("org.jetbrains.plugins.ruby") ||
            parentDescriptor.id.getIdString().equals("JavaScript"))) {
        throw new PluginException("Sub descriptor must not specify package if one is not specified for main plugin descriptor",
                                  parentDescriptor.id);
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

  private void addClassloaderIfDependencyEnabled(@NotNull PluginId dependencyId, @NotNull IdeaPluginDescriptorImpl dependent) {
    IdeaPluginDescriptorImpl dependency = idMap.get(dependencyId);
    if (dependency == null) {
      return;
    }

    // must be first to ensure that it is used first to search classes (very important if main plugin descriptor doesn't have package prefix)
    // check dependencies between optional descriptors (aka modules in a new model) from different plugins
    if (SEPARATE_CLASSLOADER_FOR_SUB &&
        dependency.contentDescriptor != null && dependent.dependenciesDescriptor != null && dependency.pluginDependencies != null) {
      for (ModuleDependenciesDescriptor.ModuleItem dependentModuleDependency : dependent.dependenciesDescriptor.modules) {
        PluginContentDescriptor.ModuleItem dependencyContentModule = dependency.contentDescriptor.findModuleByName(dependentModuleDependency.name);
        if (dependencyContentModule != null) {
          for (PluginDependency dependencyPluginDependency : dependency.pluginDependencies) {
            if (!dependencyPluginDependency.isDisabledOrBroken &&
                dependencyPluginDependency.subDescriptor != null &&
                dependentModuleDependency.packageName.equals(dependencyPluginDependency.subDescriptor.packagePrefix)) {
              loaders.add(dependencyPluginDependency.subDescriptor.getClassLoader());
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

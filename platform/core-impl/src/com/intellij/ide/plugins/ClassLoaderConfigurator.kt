// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.cl.ResolveScopeManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.lang.ClassPath
import com.intellij.util.lang.ResourceFile
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BiFunction
import java.util.function.Function

private val DEFAULT_CLASSLOADER_CONFIGURATION = UrlClassLoader.build().useCache()

@ApiStatus.Internal
class ClassLoaderConfigurator(
  val pluginSet: PluginSet,
  coreLoaderOverride: ClassLoader? = null,
) {
  private val coreLoader: ClassLoader = coreLoaderOverride ?: ClassLoaderConfigurator::class.java.classLoader

  // todo for dynamic reload this guard doesn't contain all used plugin prefixes
  private val pluginPackagePrefixUniqueGuard = HashMap<String, IdeaPluginDescriptorImpl>()
  @Suppress("JoinDeclarationAndAssignment")
  private val resourceFileFactory: Function<Path, ResourceFile>?

  private val mainToClassPath = IdentityHashMap<PluginId, MainPluginDescriptorClassPathInfo>()

  init {
    resourceFileFactory = try {
      @Suppress("UNCHECKED_CAST")
      MethodHandles.lookup().findStatic(coreLoader.loadClass("com.intellij.util.lang.PathClassLoader"), "getResourceFileFactory", MethodType.methodType(Function::class.java))
        .invokeExact() as Function<Path, ResourceFile>
    }
    catch (ignore: ClassNotFoundException) {
      null
    }
    catch (e: Throwable) {
      LOG.error(e)
      null
    }
  }

  fun configureDescriptorDynamic(subDescriptor: IdeaPluginDescriptorImpl): Boolean {
    assert(subDescriptor is ContentModuleDescriptor || subDescriptor is DependsSubDescriptor) { subDescriptor }
    if (subDescriptor is DependsSubDescriptor) {
      subDescriptor.isMarkedForLoading = false
      return false
    }
    val mainDescriptor = subDescriptor.getMainDescriptor()
    val pluginId = mainDescriptor.pluginId
    assert(pluginId == subDescriptor.pluginId) { "pluginId '$pluginId' != moduleDescriptor.pluginId '${subDescriptor.pluginId}'"}
    // class cast fails in case IU is running from sources, IDEA-318252
    (mainDescriptor.pluginClassLoader as? PluginClassLoader)?.let {
      mainToClassPath.put(pluginId, MainPluginDescriptorClassPathInfo(classLoader = it))
    }
    return configureModule(subDescriptor as ContentModuleDescriptor)
  }

  fun configure() {
    for (module in pluginSet.getEnabledModules()) {
      configureModule(module)
    }
  }

  fun configureModule(module: PluginModuleDescriptor): Boolean {
    checkPackagePrefixUniqueness(module)

    when (module) {
      is PluginMainDescriptor -> {
        if (module.useCoreClassLoader || module.pluginId == PluginManagerCore.CORE_ID) {
          setClassLoaderForModuleAndDependsSubDescriptors(module, coreLoader)
        }
        else {
          configureMainPluginModule(module)
        }
        return true
      }
      is ContentModuleDescriptor -> {
        return configureContentModule(module)
      }
    }
  }

  private fun configureContentModule(module: ContentModuleDescriptor): Boolean {
    if (module.packagePrefix == null && module.pluginId != PluginManagerCore.CORE_ID && module.jarFiles == null && module.moduleLoadingRule != ModuleLoadingRule.EMBEDDED) {
      throw PluginException("Package is not specified (module=$module)", module.pluginId)
    }

    assert(module.dependencies.isEmpty()) { "Module $module shouldn't have plugin dependencies: ${module.dependencies}" }
    val dependencies = getSortedDependencies(module)
    // if the module depends on an unavailable plugin, it will not be loaded
    val missingDependency = dependencies.find { it.pluginClassLoader == null }
    if (missingDependency != null) {
      LOG.debug { "content module $module is missing dependency $missingDependency" }
      return false
    }

    if (module.useCoreClassLoader) {
      module.pluginClassLoader = coreLoader
      return true
    }
    if (module.pluginId == PluginManagerCore.CORE_ID) {
      if (module.moduleLoadingRule == ModuleLoadingRule.EMBEDDED) {
        module.pluginClassLoader = coreLoader
      }
      else {
        configureCorePluginContentModuleClassLoader(module, dependencies)
      }
      return true
    }

    val mainInfo = mainToClassPath.get(module.pluginId) ?: run {
      configureMainPluginModule(module.getMainDescriptor())
    }

    if (module.moduleLoadingRule == ModuleLoadingRule.EMBEDDED) {
      module.pluginClassLoader = mainInfo.mainClassLoader
      return true
    }

    val customJarFiles = module.jarFiles
    if (customJarFiles == null) {
      module.pluginClassLoader = PluginClassLoader(
        classPath = mainInfo.classPath,
        parents = dependencies,
        pluginDescriptor = module,
        coreLoader = coreLoader,
        resolveScopeManager = createModuleResolveScopeManager(),
        packagePrefix = module.packagePrefix,
        libDirectories = mainInfo.libDirectories,
      )
    }
    else {
      val mimicJarUrlConnection = module.vendor == PluginManagerCore.VENDOR_JETBRAINS
                                  && (module.moduleId.id == "intellij.rider.test.cases"
                                      || module.moduleId.id == "intellij.rider.plugins.efCore.test.cases"
                                      || module.moduleId.id == "intellij.rider.plugins.for.tea.test.cases"
                                      || module.moduleId.id == "intellij.rider.plugins.fsharp.test.cases"
                                      || module.moduleId.id == "intellij.rider.plugins.godot.test.cases"
                                      || module.moduleId.id == "intellij.rider.plugins.unity.test.cases"
                                      || module.moduleId.id == "intellij.rider.plugins.unreal.link.test.cases"
                                      || module.moduleId.id == "intellij.rider.test.cases.qodana"
                                      || module.moduleId.id == "intellij.rider.test.cases.supplementary"
                                      || module.moduleId.id == "intellij.rider.test.cases.consoles"
                                      || module.moduleId.id == "intellij.rider.test.cases.rdct")
      module.pluginClassLoader = PluginClassLoader(
        classPath = ClassPath(customJarFiles, DEFAULT_CLASSLOADER_CONFIGURATION, resourceFileFactory, mimicJarUrlConnection),
        parents = dependencies,
        pluginDescriptor = module,
        coreLoader = coreLoader,
        resolveScopeManager = null,
        packagePrefix = null,
        libDirectories = mainInfo.libDirectories,
      )
    }
    return true
  }

  private fun getSortedDependencies(module: PluginModuleDescriptor): Array<PluginModuleDescriptor> {
    val dependenciesList = pluginSet.getSortedDependencies(module)
    var mutableDependenciesList: MutableList<PluginModuleDescriptor>? = null
    if (module is PluginMainDescriptor) {
      for (module in module.contentModules) {
        if (module.moduleLoadingRule == ModuleLoadingRule.EMBEDDED) {
          if (mutableDependenciesList == null) {
            mutableDependenciesList = dependenciesList.toMutableList()
          }
          mutableDependenciesList.addAll(pluginSet.getSortedDependencies(module))
        }
      }
    }
    val dependencies = (mutableDependenciesList ?: dependenciesList).toTypedArray()
    sortDependenciesInPlace(dependencies)
    return dependencies
  }

  private fun configureMainPluginModule(mainDescriptor: PluginMainDescriptor): MainPluginDescriptorClassPathInfo {
    val exisingMainInfo = mainToClassPath.get(mainDescriptor.pluginId)
    if (exisingMainInfo != null) {
      return exisingMainInfo
    } 

    var mainModuleFiles = mainDescriptor.jarFiles
    if (mainModuleFiles == null) {
      if (!mainDescriptor.useIdeaClassLoader) {
        LOG.error("jarFiles is not set for $mainDescriptor")
      }
      mainModuleFiles = emptyList()
    }
    var allFiles: MutableSet<Path>? = null
    for (contentModule in mainDescriptor.contentModules) {
      if (contentModule.moduleLoadingRule == ModuleLoadingRule.EMBEDDED) {
        val customJarFiles = contentModule.jarFiles
        if (customJarFiles != null) {
          if (allFiles == null) {
            allFiles = LinkedHashSet(mainModuleFiles)
          }
          allFiles.addAll(customJarFiles)
        }
      }
    }

    var libDirectories = Collections.emptyList<Path>()
    val libDir = mainDescriptor.pluginPath.resolve("lib")
    if (Files.exists(libDir)) {
      libDirectories = Collections.singletonList(libDir)
    }

    val mimicJarUrlConnection = !mainDescriptor.isBundled && mainDescriptor.vendor != "JetBrains"
    val files = allFiles?.toList() ?: mainModuleFiles
    val pluginClassPath = ClassPath(/* files = */ files,
                                    /* configuration = */ DEFAULT_CLASSLOADER_CONFIGURATION,
                                    /* resourceFileFactory = */ resourceFileFactory,
                                    /* mimicJarUrlConnection = */ mimicJarUrlConnection)
    val mainClassLoader = if (mainDescriptor.useIdeaClassLoader) {
      configureUsingIdeaClassloader(files, mainDescriptor)
    }
    else {
      createPluginClassLoader(module = mainDescriptor, dependencies = getSortedDependencies(mainDescriptor), classPath = pluginClassPath, libDirectories = libDirectories)
    }
    val mainInfo = MainPluginDescriptorClassPathInfo(classPath = pluginClassPath, libDirectories = libDirectories, mainClassLoader = mainClassLoader)
    mainToClassPath.put(mainDescriptor.pluginId, mainInfo)
    setClassLoaderForModuleAndDependsSubDescriptors(mainDescriptor, mainClassLoader)
    return mainInfo
  }

  private fun setClassLoaderForModuleAndDependsSubDescriptors(module: IdeaPluginDescriptorImpl, mainClassLoader: ClassLoader) {
    module.pluginClassLoader = mainClassLoader
    for (dependency in module.dependencies) {
      val subDescriptor = dependency.subDescriptor ?: continue
      if (pluginSet.findEnabledPlugin(dependency.pluginId)?.takeIf { it !== module } == null) {
        continue
      }
      if (!isKotlinPlugin(module.pluginId) &&
          isKotlinPlugin(dependency.pluginId) &&
          isIncompatibleWithKotlinPlugin(module)
      ) {
        LOG.error("unexpected condition $module") // TODO drop this branch, probably dead code, should be handled by plugin init
        // disable dependencies which optionally deepened on Kotlin plugin which are incompatible with Kotlin Plugin K2 mode KTIJ-24797
        continue
      }
      setClassLoaderForModuleAndDependsSubDescriptors(subDescriptor, mainClassLoader)
    }
  }

  private fun configureCorePluginContentModuleClassLoader(module: ContentModuleDescriptor, deps: Array<PluginModuleDescriptor>) {
    val jarFiles = module.jarFiles
    if (jarFiles != null) {
      module.pluginClassLoader = PluginClassLoader(
        classPath = ClassPath(jarFiles, DEFAULT_CLASSLOADER_CONFIGURATION, resourceFileFactory, false),
        parents = deps,
        pluginDescriptor = module,
        coreLoader = if (module.isIndependentFromCoreClassLoader) coreLoader.parent else coreLoader,
        resolveScopeManager = null,
        packagePrefix = module.packagePrefix,
        libDirectories = ArrayList(),
      )
      return
    }

    val coreUrlClassLoader = getCoreUrlClassLoaderIfPossible()
    if (coreUrlClassLoader == null) {
      assert(module.dependencies.isEmpty())
      module.pluginClassLoader = coreLoader
      return
    }

    module.pluginClassLoader = PluginClassLoader(
      classPath = coreUrlClassLoader.classPath,
      parents = deps,
      pluginDescriptor = module,
      coreLoader = coreLoader,
      resolveScopeManager = createModuleResolveScopeManager(),
      packagePrefix = module.packagePrefix,
      libDirectories = ArrayList(),
    )
  }

  private fun getCoreUrlClassLoaderIfPossible(): UrlClassLoader? {
    val coreUrlClassLoader = coreLoader as? UrlClassLoader ?: return null
    if (coreUrlClassLoader.resolveScopeManager == null) {
      val corePlugin = pluginSet.enabledPlugins.first()
      assert(corePlugin.pluginId == PluginManagerCore.CORE_ID)
      val resolveScopeManager = createPluginDependencyAndContentBasedScope(descriptor = corePlugin, pluginSet = pluginSet)
      if (resolveScopeManager != null) {
        coreUrlClassLoader.resolveScopeManager = BiFunction { name, force ->
          resolveScopeManager.isDefinitelyAlienClass(name = name, packagePrefix = "", force = force)
        }
      }
    }

    return coreUrlClassLoader
  }

  private fun checkPackagePrefixUniqueness(module: PluginModuleDescriptor) {
    val packagePrefix = module.packagePrefix
    if (packagePrefix != null) {
      val old = pluginPackagePrefixUniqueGuard.putIfAbsent(packagePrefix, module)
      if (old != null) {
        throw PluginException("Package prefix $packagePrefix is already used (second=$module, first=$old)", module.pluginId)
      }
    }
  }

  private fun createPluginClassLoader(
    module: IdeaPluginDescriptorImpl,
    dependencies: Array<PluginModuleDescriptor>,
    classPath: ClassPath,
    libDirectories: List<Path>
  ): PluginClassLoader {
    val resolveScopeManager: ResolveScopeManager? = if (module is PluginMainDescriptor) {
       if (module.pluginId.idString == "com.intellij.diagram") {
        // multiple packages - intellij.diagram and intellij.diagram.impl modules
        createScopeWithExtraPackage("com.intellij.diagram.")
      }
      else {
        createPluginDependencyAndContentBasedScope(descriptor = module, pluginSet = pluginSet)
      }
    }
    else {
      createModuleResolveScopeManager()
    }
    return PluginClassLoader(classPath = classPath,
                             parents = dependencies,
                             pluginDescriptor = module,
                             coreLoader = coreLoader,
                             resolveScopeManager = resolveScopeManager,
                             packagePrefix = module.packagePrefix,
                             libDirectories = libDirectories)
  }
}

private fun createModuleResolveScopeManager(): ResolveScopeManager {
  return object : ResolveScopeManager {
    override fun isDefinitelyAlienClass(name: String, packagePrefix: String, force: Boolean): String? {
      // the force flag is ignored for module - e.g., RailsViewLineMarkerProvider is referenced
      // as extension implementation in several modules
      return if (!name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier")) "" else null
    }
  }
}

private fun createScopeWithExtraPackage(@Suppress("SameParameterValue") customPackage: String): ResolveScopeManager {
  return object : ResolveScopeManager {
    override fun isDefinitelyAlienClass(name: String, packagePrefix: String, force: Boolean): String? {
      if (!force &&
          !name.startsWith(packagePrefix) &&
          !name.startsWith("com.intellij.ultimate.PluginVerifier") &&
          !name.startsWith(customPackage)) {
        return ""
      }
      else {
        return null
      }
    }
  }
}

// package of module is not taken in an account to support resolving of module libraries -
// instead, only classes from plugin's modules (content or dependencies) are excluded.
@VisibleForTesting
@ApiStatus.Internal
fun createPluginDependencyAndContentBasedScope(descriptor: PluginMainDescriptor, pluginSet: PluginSet): ResolveScopeManager? {
  val contentPackagePrefixes = getPackagePrefixesLoadedBySeparateClassLoaders(descriptor)
  val dependencyPackagePrefixes = getDependencyPackagePrefixes(descriptor, pluginSet)
  if (contentPackagePrefixes.isEmpty() && dependencyPackagePrefixes.isEmpty()) {
    return null
  }

  val pluginId = descriptor.pluginId.idString
  return object : ResolveScopeManager {
    override fun isDefinitelyAlienClass(name: String, packagePrefix: String, force: Boolean): String? {
      if (force) {
        return null
      }

      for ((prefix, moduleId) in contentPackagePrefixes) {
        if (name.startsWith(prefix)) {
          return "Class $name must not be requested from main classloader of $pluginId plugin. Matches content module " +
                 "(packagePrefix=$prefix, moduleId=$moduleId)."
        }
      }

      for (prefix in dependencyPackagePrefixes) {
        if (name.startsWith(prefix)) {
          return ""
        }
      }

      return null
    }
  }
}

private fun getPackagePrefixesLoadedBySeparateClassLoaders(descriptor: PluginMainDescriptor): List<Pair<String, PluginModuleId?>> {
  val modules = descriptor.contentModules
  if (modules.isEmpty()) {
    return emptyList()
  }

  val result = ArrayList<Pair<String, PluginModuleId?>>(modules.size)
  for (module in modules) {
    if (!module.jarFiles.isNullOrEmpty() || module.moduleLoadingRule == ModuleLoadingRule.EMBEDDED) {
      continue
    }

    val packagePrefix = module.packagePrefix
    if (packagePrefix == null) {
      if (module.pluginClassLoader == null) {
        continue
      }
      else {
        // If jarFiles is not set for a module, the only way to separate it is by package prefix. Therefore, we require the package prefix.
        throw PluginException("Package is not specified (module=$module)", module.pluginId)
      }
    }
    result.add("$packagePrefix." to module.moduleId)
  }
  return result
}

private fun getDependencyPackagePrefixes(descriptor: PluginMainDescriptor, pluginSet: PluginSet): List<String> {
  val dependencies = descriptor.moduleDependencies.modules
  if (dependencies.isEmpty()) {
    return Collections.emptyList()
  }

  val result = ArrayList<String>(dependencies.size)
  for (item in dependencies) {
    val packagePrefix = (pluginSet.findEnabledModule(item) ?: continue).packagePrefix
    // intellij.platform.commercial.verifier is injected
    if (packagePrefix != null && item.id != "intellij.platform.commercial.verifier") {
      result.add("$packagePrefix.")
    }
  }
  return result
}

internal val canExtendIdeaClassLoader: Boolean by lazy {
  runCatching {
    MethodHandles.lookup().findVirtual(ClassLoaderConfigurator::class.java.classLoader.javaClass, "addFiles",
                                       MethodType.methodType(Void.TYPE, MutableList::class.java))
  }.isSuccess
}

private fun configureUsingIdeaClassloader(classPath: List<Path>, descriptor: IdeaPluginDescriptorImpl): ClassLoader {
  LOG.warn("deprecated `use-idea-classloader` attribute used by $descriptor")
  val loader = ClassLoaderConfigurator::class.java.classLoader
  try {
    // `UrlClassLoader#addPath` can't be invoked directly, because the core classloader is created at bootstrap in a "lost" branch
    val addFiles = MethodHandles.lookup().findVirtual(loader.javaClass, "addFiles",
                                                      MethodType.methodType(Void.TYPE, MutableList::class.java))
    addFiles.invoke(loader, classPath)
    return loader
  }
  catch (e: Throwable) {
    throw IllegalStateException("An unexpected core classloader: $loader", e)
  }
}

@VisibleForTesting
@ApiStatus.Internal
fun sortDependenciesInPlace(dependencies: Array<out IdeaPluginDescriptorImpl>) {
  if (dependencies.size <= 1) {
    return
  }

  fun getWeight(module: IdeaPluginDescriptorImpl) = if (module is ContentModuleDescriptor) 0 else 1

  // java sort is stable, so, it is safe to not use topological comparator here
  dependencies.sortWith { o1, o2 ->
    // parent plugin must be after content module because otherwise will be asserted about requesting class from the main classloader
    val diff = getWeight(o1) - getWeight(o2)
    if (diff == 0) {
      (o2.packagePrefix ?: "").compareTo(o1.packagePrefix ?: "")
    }
    else {
      diff
    }
  }
}

private class MainPluginDescriptorClassPathInfo(
  @JvmField val classPath: ClassPath,
  @JvmField val libDirectories: List<Path>,
  @JvmField val mainClassLoader: ClassLoader,
) {
  constructor(classLoader: PluginClassLoader) 
    : this(classPath = classLoader.classPath, libDirectories = classLoader.getLibDirectories(), mainClassLoader = classLoader)
}

@Suppress("SSBasedInspection") // do not use class reference here
private val LOG: Logger
  get() = Logger.getInstance("#com.intellij.ide.plugins.ClassLoaderConfigurator")
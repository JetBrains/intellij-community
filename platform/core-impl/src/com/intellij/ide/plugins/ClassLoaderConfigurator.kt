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
  private val coreLoader: ClassLoader = ClassLoaderConfigurator::class.java.classLoader,
) {
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

  fun configureDependency(mainDescriptor: IdeaPluginDescriptorImpl, moduleDescriptor: IdeaPluginDescriptorImpl): Boolean {
    assert(mainDescriptor != moduleDescriptor) { "$mainDescriptor != $moduleDescriptor" }

    val pluginId = mainDescriptor.pluginId
    assert(pluginId == moduleDescriptor.pluginId) { "pluginId '$pluginId' != moduleDescriptor.pluginId '${moduleDescriptor.pluginId}'"}

    // class cast fails in case IU is running from sources, IDEA-318252
    (mainDescriptor.pluginClassLoader as? PluginClassLoader)?.let {
      mainToClassPath.put(pluginId, MainPluginDescriptorClassPathInfo(classLoader = it))
    }

    if (mainDescriptor.dependencies.find { it.subDescriptor === moduleDescriptor && it.isOptional } != null) {
      // dynamically enabled optional dependency module in old format
      // based on what's happening in [configureDependenciesInOldFormat]
      assert(moduleDescriptor.pluginClassLoader == null) {
        "sub descriptor $moduleDescriptor of $mainDescriptor seems to be already enabled"
      }
      val mainDependentClassLoader = mainDescriptor.pluginClassLoader
      assert(mainDependentClassLoader != null) { "plugin $mainDescriptor is not yet enabled"}
      setClassLoaderForModuleAndDependsSubDescriptors(moduleDescriptor, mainDependentClassLoader!!)
      return true
    }

    return configureModule(moduleDescriptor)
  }

  fun configure() {
    for (module in pluginSet.getEnabledModules()) {
      configureModule(module)
    }
  }

  fun configureModule(module: IdeaPluginDescriptorImpl): Boolean {
    checkPackagePrefixUniqueness(module)

    if (module.isMainPluginDescriptor) {
      if (module.useCoreClassLoader || module.pluginId == PluginManagerCore.CORE_ID) {
        setClassLoaderForModuleAndDependsSubDescriptors(module, coreLoader)
      }
      else {
        configureMainPluginModule(module)
      }
      return true
    }
    else {
      assert(module.isContentModuleDescriptor)
      return configureContentModule(module)
    }
  }

  private fun configureContentModule(module: IdeaPluginDescriptorImpl): Boolean {
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
      val mainDescriptor = pluginSet.findEnabledPlugin(module.pluginId) ?: throw PluginException("Plugin ${module.pluginId} is not found in enabled plugins", module.pluginId)
      configureMainPluginModule(mainDescriptor)
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
                                  && (module.moduleName == "intellij.rider.test.cases"
                                      || module.moduleName == "intellij.rider.plugins.efCore.test.cases"
                                      || module.moduleName == "intellij.rider.plugins.for.tea.test.cases"
                                      || module.moduleName == "intellij.rider.plugins.fsharp.test.cases"
                                      || module.moduleName == "intellij.rider.plugins.godot.test.cases"
                                      || module.moduleName == "intellij.rider.plugins.unity.test.cases"
                                      || module.moduleName == "intellij.rider.plugins.unreal.link.test.cases"
                                      || module.moduleName == "intellij.rider.test.cases.qodana"
                                      || module.moduleName == "intellij.rider.test.cases.supplementary"
                                      || module.moduleName == "intellij.rider.test.cases.consoles"
                                      || module.moduleName == "intellij.rider.test.cases.rdct")
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

  private fun getSortedDependencies(module: IdeaPluginDescriptorImpl): Array<IdeaPluginDescriptorImpl> {
    val dependenciesList = pluginSet.getSortedDependencies(module)
    var mutableDependenciesList: MutableList<IdeaPluginDescriptorImpl>? = null
    for (moduleItem in module.content.modules) {
      if (moduleItem.loadingRule == ModuleLoadingRule.EMBEDDED) {
        if (mutableDependenciesList == null) {
          mutableDependenciesList = dependenciesList.toMutableList()
        }
        mutableDependenciesList.addAll(pluginSet.getSortedDependencies(moduleItem.requireDescriptor()))
      }
    }
    val dependencies = (mutableDependenciesList ?: dependenciesList).toTypedArray()
    sortDependenciesInPlace(dependencies)
    return dependencies
  }

  private fun configureMainPluginModule(mainDescriptor: IdeaPluginDescriptorImpl): MainPluginDescriptorClassPathInfo {
    assert(mainDescriptor.isMainPluginDescriptor)
    val exisingMainInfo = mainToClassPath.get(mainDescriptor.pluginId)
    if (exisingMainInfo != null) {
      return exisingMainInfo
    } 

    var mainModuleFiles = mainDescriptor.jarFiles
    if (mainModuleFiles == null) {
      if (!mainDescriptor.isUseIdeaClassLoader) {
        LOG.error("jarFiles is not set for $mainDescriptor")
      }
      mainModuleFiles = emptyList()
    }
    var allFiles: MutableSet<Path>? = null
    for (contentModule in mainDescriptor.content.modules) {
      if (contentModule.loadingRule == ModuleLoadingRule.EMBEDDED) {
        val customJarFiles = contentModule.requireDescriptor().jarFiles
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
    val mainClassLoader = if (mainDescriptor.isUseIdeaClassLoader) {
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

  private fun setClassLoaderForModuleAndDependsSubDescriptors(module: IdeaPluginDescriptorImpl, mainDependentClassLoader: ClassLoader) {
    module.pluginClassLoader = mainDependentClassLoader
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
      setClassLoaderForModuleAndDependsSubDescriptors(subDescriptor, mainDependentClassLoader)
    }
  }

  private fun configureCorePluginContentModuleClassLoader(module: IdeaPluginDescriptorImpl, deps: Array<IdeaPluginDescriptorImpl>) {
    assert(module.isContentModuleDescriptor)
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

  private fun checkPackagePrefixUniqueness(module: IdeaPluginDescriptorImpl) {
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
    dependencies: Array<IdeaPluginDescriptorImpl>,
    classPath: ClassPath,
    libDirectories: List<Path>
  ): PluginClassLoader {
    val resolveScopeManager: ResolveScopeManager?
    // main plugin descriptor
    if (module.moduleName == null) {
      resolveScopeManager = if (module.pluginId.idString == "com.intellij.diagram") {
        // multiple packages - intellij.diagram and intellij.diagram.impl modules
        createScopeWithExtraPackage("com.intellij.diagram.")
      }
      else {
        createPluginDependencyAndContentBasedScope(descriptor = module, pluginSet = pluginSet)
      }
    }
    else {
      resolveScopeManager = if (module.content.modules.isEmpty()) {
        createModuleResolveScopeManager()
      }
      else {
        // see "The `content.module` element" section about content handling for a module
        createModuleContentBasedScope(descriptor = module)
      }
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
fun createPluginDependencyAndContentBasedScope(descriptor: IdeaPluginDescriptorImpl, pluginSet: PluginSet): ResolveScopeManager? {
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

      for ((prefix, moduleName) in contentPackagePrefixes) {
        if (name.startsWith(prefix)) {
          return "Class $name must not be requested from main classloader of $pluginId plugin. Matches content module " +
                 "(packagePrefix=$prefix, moduleName=$moduleName)."
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

private fun getPackagePrefixesLoadedBySeparateClassLoaders(descriptor: IdeaPluginDescriptorImpl): List<Pair<String, String?>> {
  val modules = descriptor.content.modules
  if (modules.isEmpty()) {
    return emptyList()
  }

  val result = ArrayList<Pair<String, String?>>(modules.size)
  for (item in modules) {
    val module = item.requireDescriptor()
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
    result.add("$packagePrefix." to module.moduleName)
  }
  return result
}

private fun getDependencyPackagePrefixes(descriptor: IdeaPluginDescriptorImpl, pluginSet: PluginSet): List<String> {
  val dependencies = descriptor.moduleDependencies.modules
  if (dependencies.isEmpty()) {
    return Collections.emptyList()
  }

  val result = ArrayList<String>(dependencies.size)
  for (item in dependencies) {
    val packagePrefix = (pluginSet.findEnabledModule(item.name) ?: continue).packagePrefix
    // intellij.platform.commercial.verifier is injected
    if (packagePrefix != null && item.name != "intellij.platform.commercial.verifier") {
      result.add("$packagePrefix.")
    }
  }
  return result
}

private fun createModuleContentBasedScope(descriptor: IdeaPluginDescriptorImpl): ResolveScopeManager {
  val packagePrefixes = ArrayList<String>(descriptor.content.modules.size)
  for (item in descriptor.content.modules) {
    packagePrefixes.add("${item.requireDescriptor().packagePrefix!!}.")
  }

  // the force flag is ignored for module - e.g., RailsViewLineMarkerProvider is referenced as extension implementation in several modules
  return object : ResolveScopeManager {
    override fun isDefinitelyAlienClass(name: String, packagePrefix: String, force: Boolean): String? {
      if (name.startsWith(packagePrefix) || name.startsWith("com.intellij.ultimate.PluginVerifier")) {
        return null
      }

      // For a module, the referenced module doesn't have own classloader and is added directly to classpath,
      // so if name doesn't pass standard package prefix filter.
      // Check that it is not in content - if in content, then it means that class is not alien.
      for (prefix in packagePrefixes) {
        if (name.startsWith(prefix)) {
          return null
        }
      }
      return ""
    }
  }
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
fun sortDependenciesInPlace(dependencies: Array<IdeaPluginDescriptorImpl>) {
  if (dependencies.size <= 1) {
    return
  }

  fun getWeight(module: IdeaPluginDescriptorImpl) = if (module.moduleName == null) 1 else 0

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
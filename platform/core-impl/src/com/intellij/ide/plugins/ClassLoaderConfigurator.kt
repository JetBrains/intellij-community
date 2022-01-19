// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.SmartList
import com.intellij.util.lang.ClassPath
import com.intellij.util.lang.ResourceFile
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus
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

  private val mainToClassPath = IdentityHashMap<PluginId, MainInfo>()

  private class MainInfo(
    @JvmField val classPath: ClassPath,
    @JvmField val files: List<Path>,
    @JvmField val libDirectories: MutableList<String>
  )

  init {
    resourceFileFactory = try {
      @Suppress("UNCHECKED_CAST")
      MethodHandles.lookup().findStatic(coreLoader.loadClass("com.intellij.util.lang.PathClassLoader"), "getResourceFileFactory",
                                        MethodType.methodType(Function::class.java))
        .invokeExact() as Function<Path, ResourceFile>
    }
    catch (ignore: ClassNotFoundException) {
      null
    }
    catch (e: Throwable) {
      log.error(e)
      null
    }
  }

  fun configureDependenciesIfNeeded(mainToModule: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>) {
    for ((mainDependent, modules) in mainToModule) {
      val mainDependentClassLoader = mainDependent.pluginClassLoader as PluginClassLoader
      mainToClassPath.put(mainDependent.pluginId, MainInfo(classPath = mainDependentClassLoader.classPath,
                                                           files = mainDependentClassLoader.files,
                                                           libDirectories = mainDependentClassLoader.libDirectories))
      if (mainDependent.packagePrefix == null) {
        for (module in modules) {
          module.pluginClassLoader = mainDependentClassLoader
        }
      }
      else {
        for (module in modules) {
          configureModule(module)
        }
      }
    }
  }

  fun configure() {
    for (module in pluginSet.getRawListOfEnabledModules()) {
      configureModule(module)
    }
  }

  fun configureModule(module: IdeaPluginDescriptorImpl) {
    checkPackagePrefixUniqueness(module)

    val isMain = module.moduleName == null
    val dependencies = pluginSet.moduleGraph.getDependencies(module).toTypedArray()
    sortDependenciesInPlace(dependencies)

    if (isMain) {
      if (module.useCoreClassLoader || module.pluginId == PluginManagerCore.CORE_ID) {
        setPluginClassLoaderForModuleAndOldSubDescriptors(module, coreLoader)
        return
      }

      var files = module.jarFiles
      if (files == null) {
        if (!module.isUseIdeaClassLoader) {
          log.error("jarFiles is not set for $module")
        }
        files = Collections.emptyList()!!
      }

      val libDirectories: MutableList<String> = SmartList()
      val libDir = module.path.resolve("lib")
      if (Files.exists(libDir)) {
        libDirectories.add(libDir.toAbsolutePath().toString())
      }

      val mimicJarUrlConnection = !module.isBundled && module.vendor != "JetBrains"
      val pluginClassPath = ClassPath(files, DEFAULT_CLASSLOADER_CONFIGURATION, resourceFileFactory, mimicJarUrlConnection)
      val mainInfo = MainInfo(classPath = pluginClassPath, files = files, libDirectories = libDirectories)
      val existing = mainToClassPath.put(module.pluginId, mainInfo)
      if (existing != null) {
        log.error(PluginException("Main module with ${module.pluginId} is already added (existingClassPath=${existing.files}",
                                  module.pluginId))
      }

      val mainDependentClassLoader = if (module.isUseIdeaClassLoader) {
        configureUsingIdeaClassloader(mainInfo.files, module)
      }
      else {
        createPluginClassLoader(module, mainInfo = mainInfo, dependencies = dependencies)
      }
      module.pluginClassLoader = mainDependentClassLoader
      configureDependenciesInOldFormat(module, mainDependentClassLoader)
    }
    else {
      if (module.packagePrefix == null) {
        throw PluginException("Package is not specified (module=$module)", module.pluginId)
      }

      assert(module.pluginDependencies.isEmpty()) { "Module $module shouldn't have plugin dependencies: ${module.pluginDependencies}" }
      for (dependency in dependencies) {
        // if the module depends on an unavailable plugin, it will not be loaded
        if (dependency.pluginClassLoader == null) {
          return
        }
      }

      if (module.useCoreClassLoader) {
        module.pluginClassLoader = coreLoader
        return
      }

      val mainInfo = mainToClassPath.get(module.pluginId)
      if (mainInfo == null) {
        if (module.pluginId == PluginManagerCore.CORE_ID) {
          configureCorePluginModuleClassLoader(module, dependencies)
        }
        else {
          throw PluginException("Cannot find containing plugin ${module.pluginId} for module ${module.moduleName} ", module.pluginId)
        }
      }
      else {
        module.pluginClassLoader = PluginClassLoader(
          mainInfo.files,
          mainInfo.classPath,
          dependencies,
          module,
          coreLoader,
          createModuleResolveScopeManager(),
          module.packagePrefix,
          mainInfo.libDirectories
        )
      }
    }
  }

  private fun configureDependenciesInOldFormat(module: IdeaPluginDescriptorImpl, mainDependentClassLoader: ClassLoader) {
    for (dependency in module.pluginDependencies) {
      val subDescriptor = dependency.subDescriptor ?: continue
      if (pluginSet.findEnabledPlugin(dependency.pluginId)?.takeIf { it !== module } == null) {
        continue
      }
      // classLoader must be set - otherwise sub descriptor considered as inactive
      subDescriptor.pluginClassLoader = mainDependentClassLoader
      configureDependenciesInOldFormat(subDescriptor, mainDependentClassLoader)
    }
  }

  private fun configureCorePluginModuleClassLoader(module: IdeaPluginDescriptorImpl, deps: Array<IdeaPluginDescriptorImpl>) {
    val coreUrlClassLoader = getCoreUrlClassLoaderIfPossible(module)
    if (coreUrlClassLoader == null) {
      setPluginClassLoaderForModuleAndOldSubDescriptors(module, coreLoader)
      return
    }

    module.pluginClassLoader = PluginClassLoader(
      Collections.emptyList(),
      coreUrlClassLoader.classPath,
      deps,
      module,
      coreLoader,
      createModuleResolveScopeManager(),
      module.packagePrefix,
      ArrayList()
    )
  }

  private fun getCoreUrlClassLoaderIfPossible(module: IdeaPluginDescriptorImpl): UrlClassLoader? {
    val coreUrlClassLoader = coreLoader as? UrlClassLoader
    if (coreUrlClassLoader == null) {
      if (!java.lang.Boolean.getBoolean("idea.use.core.classloader.for.plugin.path")) {
        @Suppress("SpellCheckingInspection")
        log.error("You must run JVM with -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
      }
      setPluginClassLoaderForModuleAndOldSubDescriptors(module, coreLoader)
      return null
    }

    if (coreUrlClassLoader.resolveScopeManager == null) {
      val corePlugin = pluginSet.enabledPlugins.first()
      assert(corePlugin.pluginId == PluginManagerCore.CORE_ID)
      val resolveScopeManager = createPluginDependencyAndContentBasedScope(descriptor = corePlugin, pluginSet = pluginSet)
      if (resolveScopeManager != null) {
        coreUrlClassLoader.resolveScopeManager = BiFunction { name, force ->
          resolveScopeManager.isDefinitelyAlienClass(name, "", force)
        }
      }
    }

    return coreUrlClassLoader
  }

  private fun setPluginClassLoaderForModuleAndOldSubDescriptors(rootDescriptor: IdeaPluginDescriptorImpl, classLoader: ClassLoader) {
    rootDescriptor.pluginClassLoader = classLoader
    for (dependency in rootDescriptor.pluginDependencies) {
      val subDescriptor = dependency.subDescriptor
      if (subDescriptor != null && pluginSet.isPluginEnabled(dependency.pluginId)) {
        setPluginClassLoaderForModuleAndOldSubDescriptors(subDescriptor, classLoader)
      }
    }
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

  private fun createPluginClassLoader(module: IdeaPluginDescriptorImpl,
                                      dependencies: Array<IdeaPluginDescriptorImpl>,
                                      mainInfo: MainInfo): PluginClassLoader {
    val resolveScopeManager: PluginClassLoader.ResolveScopeManager?
    // main plugin descriptor
    if (module.moduleName == null) {
      resolveScopeManager = when (module.pluginId.idString) {
        "com.intellij.diagram" -> {
          // multiple packages - intellij.diagram and intellij.diagram.impl modules
          createScopeWithExtraPackage("com.intellij.diagram.")
        }
        "com.intellij.struts2" -> createScopeWithExtraPackage("com.intellij.lang.ognl.")
        "com.intellij.properties" -> {
          // todo ability to customize (cannot move due to backward compatibility)
          PluginClassLoader.ResolveScopeManager { name, packagePrefix, force ->
            if (force) {
              null
            }
            else if (!name.startsWith(packagePrefix) &&
                     !name.startsWith("com.intellij.ultimate.PluginVerifier") &&
                     name != "com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider") {
              ""
            }
            else {
              null
            }
          }
        }
        else -> createPluginDependencyAndContentBasedScope(descriptor = module, pluginSet = pluginSet)
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
    return PluginClassLoader(mainInfo.files,
                             mainInfo.classPath,
                             dependencies,
                             module,
                             coreLoader,
                             resolveScopeManager,
                             module.packagePrefix,
                             mainInfo.libDirectories)
  }
}

// do not use class reference here
@Suppress("SSBasedInspection")
private val log: Logger
  get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

private fun createModuleResolveScopeManager(): PluginClassLoader.ResolveScopeManager {
  return PluginClassLoader.ResolveScopeManager { name, packagePrefix, _ ->
    // force flag is ignored for module - e.g., RailsViewLineMarkerProvider is referenced
    // as extension implementation in several modules
    if (!name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier")) "" else null
  }
}

private fun createScopeWithExtraPackage(customPackage: String): PluginClassLoader.ResolveScopeManager {
  return PluginClassLoader.ResolveScopeManager { name, packagePrefix, force ->
    if (!force &&
        !name.startsWith(packagePrefix) &&
        !name.startsWith("com.intellij.ultimate.PluginVerifier") &&
        !name.startsWith(customPackage)) {
      ""
    }
    else {
      null
    }
  }
}

// package of module is not taken in account to support resolving of module libraries -
// instead, only classes from plugin's modules (content or dependencies) are excluded.
private fun createPluginDependencyAndContentBasedScope(descriptor: IdeaPluginDescriptorImpl,
                                                       pluginSet: PluginSet): PluginClassLoader.ResolveScopeManager? {
  val contentPackagePrefixes = getContentPackagePrefixes(descriptor)
  val dependencyPackagePrefixes = getDependencyPackagePrefixes(descriptor, pluginSet)
  if (contentPackagePrefixes.isEmpty() && dependencyPackagePrefixes.isEmpty()) {
    return null
  }

  val pluginId = descriptor.pluginId.idString
  return PluginClassLoader.ResolveScopeManager { name, _, force ->
    if (force) {
      return@ResolveScopeManager null
    }

    for (prefix in contentPackagePrefixes) {
      if (name.startsWith(prefix)) {
        return@ResolveScopeManager "Class $name must be not requested from main classloader of $pluginId plugin"
      }
    }

    for (prefix in dependencyPackagePrefixes) {
      if (name.startsWith(prefix)) {
        return@ResolveScopeManager ""
      }
    }

    null
  }
}

private fun getContentPackagePrefixes(descriptor: IdeaPluginDescriptorImpl): List<String> {
  val modules = descriptor.content.modules
  if (modules.isEmpty()) {
    return Collections.emptyList()
  }

  val result = Array(modules.size) {
    val module = modules.get(it).requireDescriptor()
    "${module.packagePrefix ?: throw PluginException("Package is not specified (module=$module)", module.pluginId)}."
  }
  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  return Arrays.asList(*result)
}

private fun getDependencyPackagePrefixes(descriptor: IdeaPluginDescriptorImpl, pluginSet: PluginSet): List<String> {
  val dependencies = descriptor.dependencies.modules
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

private fun createModuleContentBasedScope(descriptor: IdeaPluginDescriptorImpl): PluginClassLoader.ResolveScopeManager {
  val packagePrefixes = ArrayList<String>(descriptor.content.modules.size)
  for (item in descriptor.content.modules) {
    packagePrefixes.add("${item.requireDescriptor().packagePrefix!!}.")
  }

  // force flag is ignored for module - e.g., RailsViewLineMarkerProvider is referenced as extension implementation in several modules
  return PluginClassLoader.ResolveScopeManager { name, packagePrefix, _ ->
    if (name.startsWith(packagePrefix!!) || name.startsWith("com.intellij.ultimate.PluginVerifier")) {
      return@ResolveScopeManager null
    }

    // For a module, the referenced module doesn't have own classloader and is added directly to classpath,
    // so, if name doesn't pass standard package prefix filter.
    // Check that it is not in content - if in content, then it means that class is not alien.
    for (prefix in packagePrefixes) {
      if (name.startsWith(prefix)) {
        return@ResolveScopeManager null
      }
    }
    ""
  }
}

private fun configureUsingIdeaClassloader(classPath: List<Path>, descriptor: IdeaPluginDescriptorImpl): ClassLoader {
  log.warn("${descriptor.pluginId} uses deprecated `use-idea-classloader` attribute")
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

fun sortDependenciesInPlace(dependencies: Array<IdeaPluginDescriptorImpl>) {
  if (dependencies.size <= 1) return

  fun getWeight(module: IdeaPluginDescriptorImpl) = if (module.moduleName == null) 1 else 0

  // java sort is stable, so, it is safe to not use topological comparator here
  Arrays.sort(dependencies, kotlin.Comparator { o1, o2 ->
    // parent plugin must be after content module because otherwise will be an assert about requesting class from the main classloader
    getWeight(o1) - getWeight(o2)
  })
}
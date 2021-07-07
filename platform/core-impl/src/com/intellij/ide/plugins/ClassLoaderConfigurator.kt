// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
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
import java.util.function.BiPredicate
import java.util.function.Function

private val DEFAULT_CLASSLOADER_CONFIGURATION = UrlClassLoader.build().useCache()
private val EMPTY_DESCRIPTOR_ARRAY = emptyArray<IdeaPluginDescriptorImpl>()

@ApiStatus.Internal
class ClassLoaderConfigurator(
  val pluginSet: PluginSet,
  private val coreLoader: ClassLoader = ClassLoaderConfigurator::class.java.classLoader,
) {

  private var javaDep: Optional<IdeaPluginDescriptorImpl>? = null

  // temporary set to produce arrays (avoid allocation for each plugin)
  // set to remove duplicated classloaders
  private val dependencies = LinkedHashSet<IdeaPluginDescriptorImpl>()

  private val hasAllModules = pluginSet.isPluginEnabled(PluginManagerCore.ALL_MODULES_MARKER)

  // todo for dynamic reload this guard doesn't contain all used plugin prefixes
  private val pluginPackagePrefixUniqueGuard = HashMap<String, IdeaPluginDescriptorImpl>()
  @Suppress("JoinDeclarationAndAssignment")
  private val resourceFileFactory: Function<Path, ResourceFile>?

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

  fun configureDependenciesIfNeeded(mainToModule: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>,
                                    dependencyPlugin: IdeaPluginDescriptorImpl) {
    for ((mainDependent, modules) in mainToModule) {
      val mainDependentClassLoader = mainDependent.classLoader as PluginClassLoader
      if (mainDependent.packagePrefix != null) {
        for (module in modules) {
          assert(module.packagePrefix != null)
          configureModule(module, mainDependent,
                          files = mainDependentClassLoader.files,
                          libDirectories = mainDependentClassLoader.libDirectories,
                          classPath = mainDependentClassLoader.classPath)
        }
      }
      else {
        mainDependentClassLoader.attachParent(dependencyPlugin)
        for (module in modules) {
          module.classLoader = mainDependentClassLoader
        }
      }
    }
    dependencies.clear()
  }

  fun configureAll() {
    val postTasks = ArrayList<() -> Unit>()
    for (plugin in pluginSet.enabledPlugins) {
      // not only for core plugin, but also for a plugin from classpath (run TraverseUi)
      if (plugin.pluginId == PluginManagerCore.CORE_ID || (plugin.isUseCoreClassLoader && !plugin.content.modules.isEmpty())) {
        configureCorePlugin(plugin, postTasks)
      }
      else {
        configure(plugin)
      }
    }

    // intellij.profiler.ultimate depends on com.intellij.java but com.intellij.java depends on core plugin
    for (postTask in postTasks) {
      postTask()
    }
  }

  fun configure(plugin: IdeaPluginDescriptorImpl) {
    checkPackagePrefixUniqueness(plugin)

    if (plugin.pluginId == PluginManagerCore.CORE_ID) {
      throw IllegalStateException("Core plugin cannot be configured dynamically")
    }
    else if (plugin.isUseCoreClassLoader) {
      setPluginClassLoaderForMainAndSubPlugins(plugin, coreLoader)
      return
    }

    dependencies.clear()

    // first, set class loader for main descriptor
    if (hasAllModules) {
      val implicitDependency = PluginManagerCore.getImplicitDependency(plugin) {
        // first, set class loader for main descriptor
        if (javaDep == null) {
          javaDep = Optional.ofNullable(pluginSet.findEnabledPlugin(PluginManagerCore.JAVA_PLUGIN_ID))
        }
        javaDep!!.orElse(null)
      }
      implicitDependency?.let {
        if (it.classLoader !== coreLoader) {
          dependencies.add(it)
        }
        Unit
      }
    }

    var files = plugin.jarFiles
    if (files == null) {
      log.error("jarFiles is not set for $plugin")
      files = Collections.emptyList()!!
    }

    var oldActiveSubModules: MutableList<IdeaPluginDescriptorImpl>? = null
    for (dependency in plugin.pluginDependencies) {
      val p = pluginSet.findEnabledPlugin(dependency.pluginId) ?: continue
      val loader = p.classLoader
      if (loader == null) {
        log.error(PluginLoadingError.formatErrorMessage(plugin, "requires missing class loader for $p"))
      }
      else if (loader !== coreLoader) {
        // e.g. `.env` plugin in an old format and doesn't explicitly specify dependency on a new extracted modules
        if (!plugin.isBundled) {
          addContentModulesIfNeeded(dependency)
        }
        // must be after adding implicit module class loaders
        dependencies.add(p)
      }

      dependency.subDescriptor?.let {
        if (oldActiveSubModules == null) {
          oldActiveSubModules = ArrayList()
        }
        oldActiveSubModules!!.add(it)
      }
    }

    // new format
    processDirectDependencies(plugin, pluginSet) {
      if (it.classLoader !== coreLoader) {
        dependencies.add(it)
      }
    }

    val mimicJarUrlConnection = !plugin.isBundled && plugin.vendor != "JetBrains"
    val pluginClassPath = ClassPath(files, Collections.emptySet(), DEFAULT_CLASSLOADER_CONFIGURATION, resourceFileFactory, mimicJarUrlConnection)

    val libDirectories: MutableList<String> = SmartList()
    val libDir = plugin.path.resolve("lib")
    if (Files.exists(libDir)) {
      libDirectories.add(libDir.toAbsolutePath().toString())
    }

    val mainDependentClassLoader = if (plugin.isUseIdeaClassLoader) {
      configureUsingIdeaClassloader(files, plugin)
    }
    else {
      createPluginClassLoader(plugin, files = files, libDirectories = libDirectories, classPath = pluginClassPath)
    }

    // second, set class loaders for sub descriptors
    plugin.classLoader = mainDependentClassLoader
    for (module in plugin.content.modules) {
      configureModule(module = module.requireDescriptor(),
                      plugin = plugin,
                      files = files,
                      libDirectories = libDirectories,
                      classPath = pluginClassPath)
    }

    for (subDescriptor in (oldActiveSubModules ?: Collections.emptyList())) {
      // classLoader must be set - otherwise sub descriptor considered as inactive
      subDescriptor.classLoader = mainDependentClassLoader
    }

    // reset to ensure that stalled data will be not reused somehow later
    dependencies.clear()
  }

  private fun addContentModulesIfNeeded(dependency: PluginDependency) {
    when (dependency.pluginId.idString) {
      "Docker" -> {
        pluginSet.findEnabledModule("intellij.clouds.docker.file")?.let {
          dependencies.add(it)
        }
        pluginSet.findEnabledModule("intellij.clouds.docker.remoteRun")?.let {
          dependencies.add(it)
        }
      }
      "com.intellij.diagram" -> {
        // https://youtrack.jetbrains.com/issue/IDEA-266323
        pluginSet.findEnabledModule("intellij.diagram.java")?.let {
          dependencies.add(it)
        }
      }
      "com.intellij.modules.clion" -> {
        pluginSet.findEnabledModule("intellij.profiler.clion")?.let {
          dependencies.add(it)
        }
      }
    }
  }

  private fun configureCorePlugin(plugin: IdeaPluginDescriptorImpl, postTasks: MutableList<() -> Unit>) {
    plugin.classLoader = coreLoader
    // do we really have pluginDependencies for core plugin?
    for (dependency in plugin.pluginDependencies) {
      if (dependency.subDescriptor != null && pluginSet.isPluginEnabled(dependency.pluginId)) {
        configureCorePlugin(dependency.subDescriptor!!, postTasks)
      }
    }

    if (plugin.content.modules.isEmpty()) {
      return
    }

    val coreUrlClassLoader = coreLoader as? UrlClassLoader
    for (item in plugin.content.modules) {
      val module = item.requireDescriptor()
      // skip if some dependency is not available
      if (module.dependencies.modules.any { !pluginSet.isModuleEnabled(it.name) } ||
          module.dependencies.plugins.any { !pluginSet.isPluginEnabled(it.id) }) {
        continue
      }

      assert(module.content.modules.isEmpty())
      if (coreUrlClassLoader == null) {
        module.classLoader = coreLoader
      }
      else {
        if (coreUrlClassLoader.resolveScopeManager == null) {
          val resolveScopeManager = createPluginDependencyAndContentBasedScope(descriptor = plugin, pluginSet = pluginSet)
          if (resolveScopeManager != null) {
            coreUrlClassLoader.resolveScopeManager = BiPredicate { name, force ->
              resolveScopeManager.isDefinitelyAlienClass(name, "", force) != null
            }
          }
        }

        if (module.dependencies.plugins.isEmpty()) {
          configureModule(module = module,
                          plugin = plugin,
                          files = Collections.emptyList(),
                          libDirectories = ArrayList(),
                          classPath = coreUrlClassLoader.classPath)
        }
        else {
          postTasks.add {
            configureModule(module = module,
                            plugin = plugin,
                            files = Collections.emptyList(),
                            libDirectories = ArrayList(),
                            classPath = coreUrlClassLoader.classPath)
          }
        }
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

  private fun createPluginClassLoader(descriptor: IdeaPluginDescriptorImpl,
                                      files: List<Path>,
                                      libDirectories: MutableList<String>,
                                      classPath: ClassPath): PluginClassLoader {
    val parents: Array<IdeaPluginDescriptorImpl> = if (dependencies.isEmpty()) {
      EMPTY_DESCRIPTOR_ARRAY
    }
    else {
      dependencies.toArray(arrayOfNulls(dependencies.size))
    }

    return createPluginClassLoader(parents = parents,
                                   descriptor = descriptor,
                                   files = files,
                                   coreLoader = coreLoader,
                                   classPath = classPath,
                                   libDirectories = libDirectories,
                                   pluginSet = pluginSet)
  }

  private fun configureModule(module: IdeaPluginDescriptorImpl,
                              plugin: IdeaPluginDescriptorImpl,
                              files: List<Path>,
                              libDirectories: MutableList<String>,
                              classPath: ClassPath) {
    if (module.packagePrefix == null) {
      throw PluginException("Package is not specified (module=$module)", module.pluginId)
    }

    checkPackagePrefixUniqueness(module)
    dependencies.clear()

    // must be before main descriptor classloader

    for (item in module.dependencies.modules) {
      // Module dependency is always optional. If the module depends on an unavailable plugin, it will not be loaded.
      dependencies.add(pluginSet.findEnabledModule(item.name) ?: return)
    }
    for (item in module.dependencies.plugins) {
      val descriptor = pluginSet.findEnabledPlugin(item.id) ?: return
      if (descriptor.classLoader !== coreLoader) {
        dependencies.add(descriptor)
      }
    }

    // add main descriptor classloader as parent
    if (plugin.id != PluginManagerCore.CORE_ID) {
      dependencies.add(plugin)
    }

    assert(module.pluginDependencies.isEmpty()) { "Module $module shouldn't have plugin dependencies: ${module.pluginDependencies}" }

    val array = if (dependencies.isEmpty()) {
      EMPTY_DESCRIPTOR_ARRAY
    }
    else {
      dependencies.toArray(arrayOfNulls(dependencies.size))
    }

    module.classLoader = PluginClassLoader(
      files,
      classPath,
      array,
      module,
      coreLoader,
      createModuleResolveScopeManager(), module.packagePrefix,
      libDirectories
    )
  }

  private fun setPluginClassLoaderForMainAndSubPlugins(rootDescriptor: IdeaPluginDescriptorImpl, classLoader: ClassLoader?) {
    rootDescriptor.classLoader = classLoader
    for (dependency in rootDescriptor.pluginDependencies) {
      if (dependency.subDescriptor != null && pluginSet.isPluginEnabled(dependency.pluginId)) {
        setPluginClassLoaderForMainAndSubPlugins(dependency.subDescriptor!!, classLoader)
      }
    }

    for (item in rootDescriptor.content.modules) {
      val module = item.requireDescriptor()
      // skip if some dependency is not available
      if (module.dependencies.modules.any { !pluginSet.isModuleEnabled(it.name) } ||
          module.dependencies.plugins.any { !pluginSet.isPluginEnabled(it.id) }) {
        continue
      }

      setPluginClassLoaderForMainAndSubPlugins(module, classLoader)
    }
  }
}

// do not use class reference here
@Suppress("SSBasedInspection")
private val log: Logger
  get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

// static to ensure that anonymous classes will not hold ClassLoaderConfigurator
private fun createPluginClassLoader(parents: Array<IdeaPluginDescriptorImpl>,
                                    descriptor: IdeaPluginDescriptorImpl,
                                    files: List<Path>,
                                    libDirectories: MutableList<String>,
                                    coreLoader: ClassLoader,
                                    classPath: ClassPath,
                                    pluginSet: PluginSet): PluginClassLoader {
  // main plugin descriptor
  if (descriptor.descriptorPath == null) {
    when (descriptor.id.idString) {
      "com.intellij.diagram" -> {
        // multiple packages - intellij.diagram and intellij.diagram.impl modules
        return createPluginClassLoaderWithExtraPackage(parents = parents,
                                                       descriptor = descriptor,
                                                       files = files,
                                                       coreLoader = coreLoader,
                                                       classPath = classPath,
                                                       libDirectories = libDirectories,
                                                       customPackage = "com.intellij.diagram.")
      }
      "com.intellij.struts2" -> {
        return createPluginClassLoaderWithExtraPackage(parents = parents,
                                                       descriptor = descriptor,
                                                       files = files,
                                                       coreLoader = coreLoader,
                                                       classPath = classPath,
                                                       libDirectories = libDirectories,
                                                       customPackage = "com.intellij.lang.ognl.")
      }
      "com.intellij.properties" -> {
        // todo ability to customize (cannot move due to backward compatibility)
        return createPluginClassloader(parents = parents,
                                       descriptor = descriptor,
                                       files = files,
                                       coreLoader = coreLoader,
                                       classPath = classPath,
                                       libDirectories = libDirectories) { name, packagePrefix, force ->
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
    }
  }
  else {
    if (!descriptor.content.modules.isEmpty()) {
      // see "The `content.module` element" section about content handling for a module
      return createPluginClassloader(parents = parents,
                                     descriptor = descriptor,
                                     files = files,
                                     coreLoader = coreLoader,
                                     classPath = classPath,
                                     libDirectories = libDirectories,
                                     resolveScopeManager = createModuleContentBasedScope(descriptor))
    }
    else if (descriptor.packagePrefix != null) {
      return createPluginClassloader(parents = parents,
                                     descriptor = descriptor,
                                     files = files,
                                     coreLoader = coreLoader,
                                     classPath = classPath,
                                     libDirectories = libDirectories, resolveScopeManager = createModuleResolveScopeManager())
    }
  }

  return createPluginClassloader(
    parents = parents,
    descriptor = descriptor,
    files = files,
    coreLoader = coreLoader,
    classPath = classPath,
    libDirectories = libDirectories,
    resolveScopeManager = createPluginDependencyAndContentBasedScope(descriptor = descriptor, pluginSet = pluginSet)
  )
}

private fun createModuleResolveScopeManager(): PluginClassLoader.ResolveScopeManager {
  return PluginClassLoader.ResolveScopeManager { name, packagePrefix, _ ->
    // force flag is ignored for module - e.g., RailsViewLineMarkerProvider is referenced
    // as extension implementation in several modules
    if (!name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier")) "" else null
  }
}

private fun createPluginClassloader(parents: Array<IdeaPluginDescriptorImpl>,
                                    descriptor: IdeaPluginDescriptorImpl,
                                    files: List<Path>,
                                    libDirectories: MutableList<String>,
                                    coreLoader: ClassLoader,
                                    classPath: ClassPath,
                                    resolveScopeManager: PluginClassLoader.ResolveScopeManager?): PluginClassLoader {
  return PluginClassLoader(files, classPath, parents, descriptor, coreLoader, resolveScopeManager, descriptor.packagePrefix,
                           libDirectories)
}

private fun createPluginClassLoaderWithExtraPackage(parents: Array<IdeaPluginDescriptorImpl>,
                                                    descriptor: IdeaPluginDescriptorImpl,
                                                    files: List<Path>,
                                                    libDirectories: MutableList<String>,
                                                    coreLoader: ClassLoader,
                                                    classPath: ClassPath,
                                                    customPackage: String): PluginClassLoader {
  return createPluginClassloader(parents = parents,
                                 descriptor = descriptor,
                                 files = files,
                                 coreLoader = coreLoader,
                                 classPath = classPath,
                                 libDirectories = libDirectories) { name, packagePrefix, force ->
    if (force) {
      null
    }
    else {
      if (!name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier") && !name.startsWith(customPackage)) {
        ""
      }
      else {
        null
      }
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
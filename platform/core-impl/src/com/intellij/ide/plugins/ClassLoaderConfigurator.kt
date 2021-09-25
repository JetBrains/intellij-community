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
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.function.Function

private val DEFAULT_CLASSLOADER_CONFIGURATION = UrlClassLoader.build().useCache()

@ApiStatus.Internal
class ClassLoaderConfigurator(
  val pluginSet: PluginSet,
  private val coreLoader: ClassLoader = ClassLoaderConfigurator::class.java.classLoader,
  private val usePluginClassLoader: Boolean = true, /* grab classes from platform loader only if nothing is found in any of plugin dependencies */
) {
  private var javaDep: Optional<IdeaPluginDescriptorImpl>? = null

  // temporary set to produce arrays (avoid allocation for each plugin)
  // set to remove duplicated classloaders
  private val loaders = LinkedHashSet<ClassLoader>()

  private val hasAllModules = pluginSet.isPluginEnabled(PluginManagerCore.ALL_MODULES_MARKER)

  // todo for dynamic reload this guard doesn't contain all used plugin prefixes
  private val pluginPackagePrefixUniqueGuard = HashSet<String>()
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
      if (ClassLoaderConfigurationData.isClassloaderPerDescriptorEnabled(mainDependent.id, mainDependent.packagePrefix)) {
        for (module in modules) {
          assert(module.packagePrefix != null)
          configureModule(module, mainDependentClassLoader,
                          files = mainDependentClassLoader.files,
                          libDirectories = mainDependentClassLoader.libDirectories,
                          classPath = mainDependentClassLoader.classPath)
        }
      }
      else {
        mainDependentClassLoader.attachParent(dependencyPlugin.classLoader!!)
        for (module in modules) {
          module.classLoader = mainDependentClassLoader
        }
      }
    }
    loaders.clear()
  }

  @JvmOverloads
  fun configure(plugin: IdeaPluginDescriptorImpl, fallbackClassLoader: ClassLoader? = null) {
    checkPackagePrefixUniqueness(plugin)

    if (plugin.pluginId == PluginManagerCore.CORE_ID || plugin.isUseCoreClassLoader) {
      setPluginClassLoaderForMainAndSubPlugins(plugin, coreLoader)
      return
    }
    if (!usePluginClassLoader) {
      setPluginClassLoaderForMainAndSubPlugins(plugin, fallbackClassLoader)
    }

    loaders.clear()

    // first, set class loader for main descriptor
    if (hasAllModules) {
      val implicitDependency = PluginManagerCore.getImplicitDependency(plugin) {
        // first, set class loader for main descriptor
        if (javaDep == null) {
          javaDep = Optional.ofNullable(pluginSet.findEnabledPlugin(PluginManagerCore.JAVA_PLUGIN_ID))
        }
        javaDep!!.orElse(null)
      }
      implicitDependency?.let { addLoaderOrLogError(plugin, it, loaders) }
    }

    var files = plugin.jarFiles
    if (files == null) {
      files = collectClassPath(plugin)
    }
    else {
      plugin.jarFiles = null
    }

    var oldActiveSubModules: MutableList<IdeaPluginDescriptorImpl>? = null
    for (dependency in plugin.pluginDependencies) {
      val p = pluginSet.findEnabledPlugin(dependency.pluginId) ?: continue
      val loader = p.classLoader
      if (loader == null) {
        log.error(PluginLoadingError.formatErrorMessage(plugin, "requires missing class loader for $p"))
      }
      else if (loader !== coreLoader) {
        loaders.add(loader)
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
      val classLoader = it.classLoader!!
      if (classLoader !== coreLoader) {
        loaders.add(classLoader)
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
    if (usePluginClassLoader) {
      plugin.classLoader = mainDependentClassLoader
      for (module in plugin.content.modules) {
        configureModule(module = module.requireDescriptor(),
                        mainDependentClassLoader = mainDependentClassLoader,
                        files = files,
                        libDirectories = libDirectories,
                        classPath = pluginClassPath)
      }

      for (subDescriptor in (oldActiveSubModules ?: Collections.emptyList())) {
        // classLoader must be set - otherwise sub descriptor considered as inactive
        subDescriptor.classLoader = mainDependentClassLoader
      }
    }
    else {
      setPluginClassLoaderForMainAndSubPlugins(plugin, mainDependentClassLoader)
    }

    // reset to ensure that stalled data will be not reused somehow later
    loaders.clear()
  }

  private fun checkPackagePrefixUniqueness(module: IdeaPluginDescriptorImpl) {
    val packagePrefix = module.packagePrefix
    if (packagePrefix != null && !pluginPackagePrefixUniqueGuard.add(packagePrefix)) {
      throw PluginException("Package prefix $packagePrefix is already used (module=$module)", module.pluginId)
    }
  }

  private fun createPluginClassLoader(descriptor: IdeaPluginDescriptorImpl,
                                      files: List<Path>,
                                      libDirectories: MutableList<String>,
                                      classPath: ClassPath): PluginClassLoader {
    val parentLoaders = if (loaders.isEmpty()) {
      PluginClassLoader.EMPTY_CLASS_LOADER_ARRAY
    }
    else {
      loaders.toArray(arrayOfNulls(loaders.size))
    }

    return createPluginClassLoader(parentLoaders = parentLoaders,
                                   descriptor = descriptor,
                                   files = files,
                                   coreLoader = coreLoader,
                                   classPath = classPath,
                                   libDirectories = libDirectories,
                                   pluginSet = pluginSet)
  }

  private fun configureModule(module: IdeaPluginDescriptorImpl,
                              mainDependentClassLoader: ClassLoader,
                              files: List<Path>,
                              libDirectories: MutableList<String>,
                              classPath: ClassPath) {
    if (module.packagePrefix == null) {
      throw PluginException("Package is not specified (module=$module)", module.pluginId)
    }

    checkPackagePrefixUniqueness(module)
    loaders.clear()

    // must be before main descriptor classloader

    for (item in module.dependencies.modules) {
      // Module dependency is always optional. If the module depends on an unavailable plugin, it will not be loaded.
      val descriptor = (pluginSet.findEnabledModule(item.name) ?: return).requireDescriptor()
      if (descriptor.classLoader !== coreLoader) {
        loaders.add(descriptor.classLoader ?: throw IllegalStateException("Class loader is not configured (module=$descriptor)"))
      }
    }
    for (item in module.dependencies.plugins) {
      val descriptor = pluginSet.findEnabledPlugin(item.id) ?: return
      if (descriptor.classLoader !== coreLoader) {
        loaders.add(descriptor.classLoader!!)
      }
    }

    // add main descriptor classloader as parent
    loaders.add(mainDependentClassLoader)

    assert(module.pluginDependencies.isEmpty())
    val subClassloader = createPluginClassLoader(module, files = files, libDirectories = libDirectories, classPath = classPath)
    module.classLoader = subClassloader
  }

  private fun addLoaderOrLogError(dependent: IdeaPluginDescriptorImpl,
                                  dependency: IdeaPluginDescriptorImpl,
                                  loaders: MutableCollection<ClassLoader>) {
    val loader = dependency.classLoader
    if (loader == null) {
      log.error(PluginLoadingError.formatErrorMessage(dependent, "requires missing class loader for '${dependency.name}'"))
    }
    else if (loader !== coreLoader) {
      loaders.add(loader)
    }
  }

  private fun setPluginClassLoaderForMainAndSubPlugins(rootDescriptor: IdeaPluginDescriptorImpl, classLoader: ClassLoader?) {
    rootDescriptor.classLoader = classLoader
    for (dependency in rootDescriptor.pluginDependencies) {
      if (dependency.subDescriptor != null) {
        val descriptor = pluginSet.findEnabledPlugin(dependency.pluginId)
        if (descriptor != null) {
          setPluginClassLoaderForMainAndSubPlugins(dependency.subDescriptor!!, classLoader)
        }
      }
    }

    m@ for (item in rootDescriptor.content.modules) {
      val module = item.requireDescriptor()

      // skip if some dependency is not available
      for (dependency in module.dependencies.modules) {
        pluginSet.findEnabledModule(dependency.name) ?: continue@m
      }
      for (dependency in module.dependencies.plugins) {
        pluginSet.findEnabledPlugin(dependency.id) ?: continue@m
      }

      setPluginClassLoaderForMainAndSubPlugins(module, classLoader)
    }
  }

  private fun collectClassPath(descriptor: IdeaPluginDescriptorImpl): List<Path> {
    val pluginPath = descriptor.path
    if (!Files.isDirectory(pluginPath)) {
      return Collections.singletonList(pluginPath)
    }

    val result = ArrayList<Path>()
    val classesDir = pluginPath.resolve("classes")
    if (Files.exists(classesDir)) {
      result.add(classesDir)
    }
    if (usePluginClassLoader) {
      val productionDirectory = pluginPath.parent
      if (productionDirectory.endsWith("production")) {
        result.add(pluginPath)
      }
    }
    try {
      Files.newDirectoryStream(pluginPath.resolve("lib")).use { childStream ->
        for (f in childStream) {
          if (Files.isRegularFile(f)) {
            val name = f.fileName.toString()
            if (name.endsWith(".jar", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)) {
              result.add(f)
            }
          }
          else {
            result.add(f)
          }
        }
      }
    }
    catch (ignore: NoSuchFileException) {
    }
    catch (e: IOException) {
      PluginManagerCore.getLogger().debug(e)
    }
    return result
  }
}

// do not use class reference here
@Suppress("SSBasedInspection")
private val log: Logger
  get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

// static to ensure that anonymous classes will not hold ClassLoaderConfigurator
private fun createPluginClassLoader(parentLoaders: Array<ClassLoader>,
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
        return createPluginClassLoaderWithExtraPackage(parentLoaders = parentLoaders,
                                                       descriptor = descriptor,
                                                       files = files,
                                                       coreLoader = coreLoader,
                                                       classPath = classPath,
                                                       libDirectories = libDirectories,
                                                       customPackage = "com.intellij.diagram.")
      }
      "com.intellij.struts2" -> {
        return createPluginClassLoaderWithExtraPackage(parentLoaders = parentLoaders,
                                                       descriptor = descriptor,
                                                       files = files,
                                                       coreLoader = coreLoader,
                                                       classPath = classPath,
                                                       libDirectories = libDirectories,
                                                       customPackage = "com.intellij.lang.ognl.")
      }
      "com.intellij.properties" -> {
        // todo ability to customize (cannot move due to backward compatibility)
        return createPluginClassloader(parentLoaders = parentLoaders,
                                       descriptor = descriptor,
                                       files = files,
                                       coreLoader = coreLoader,
                                       classPath = classPath,
                                       libDirectories = libDirectories) { name, packagePrefix, force ->
          if (force) {
            false
          }
          else {
            !name.startsWith(packagePrefix) &&
            !name.startsWith("com.intellij.ultimate.PluginVerifier") &&
            name != "com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider"
          }
        }
      }
    }
  }
  else {
    if (!descriptor.content.modules.isEmpty()) {
      // see "The `content.module` element" section about content handling for a module
      return createPluginClassloader(parentLoaders = parentLoaders,
                                     descriptor = descriptor,
                                     files = files,
                                     coreLoader = coreLoader,
                                     classPath = classPath,
                                     libDirectories = libDirectories,
                                     resolveScopeManager = createModuleContentBasedScope(descriptor))
    }
    else if (descriptor.packagePrefix != null) {
      return createPluginClassloader(parentLoaders = parentLoaders,
                                     descriptor = descriptor,
                                     files = files,
                                     coreLoader = coreLoader,
                                     classPath = classPath,
                                     libDirectories = libDirectories) { name, packagePrefix, _ ->
        // force flag is ignored for module - e.g. RailsViewLineMarkerProvider is referenced
        // as extension implementation in several modules
        !name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier")
      }
    }
  }

  return createPluginClassloader(
    parentLoaders = parentLoaders,
    descriptor = descriptor,
    files = files,
    coreLoader = coreLoader,
    classPath = classPath,
    libDirectories = libDirectories,
    resolveScopeManager = createPluginDependencyAndContentBasedScope(descriptor = descriptor, pluginSet = pluginSet)
  )
}

private fun createPluginClassloader(parentLoaders: Array<ClassLoader>,
                                    descriptor: IdeaPluginDescriptorImpl,
                                    files: List<Path>,
                                    libDirectories: MutableList<String>,
                                    coreLoader: ClassLoader,
                                    classPath: ClassPath,
                                    resolveScopeManager: PluginClassLoader.ResolveScopeManager?): PluginClassLoader {
  return PluginClassLoader(files, classPath, parentLoaders, descriptor, coreLoader, resolveScopeManager, descriptor.packagePrefix,
                           libDirectories)
}

private fun createPluginClassLoaderWithExtraPackage(parentLoaders: Array<ClassLoader>,
                                                    descriptor: IdeaPluginDescriptorImpl,
                                                    files: List<Path>,
                                                    libDirectories: MutableList<String>,
                                                    coreLoader: ClassLoader,
                                                    classPath: ClassPath,
                                                    customPackage: String): PluginClassLoader {
  return createPluginClassloader(parentLoaders = parentLoaders,
                                 descriptor = descriptor,
                                 files = files,
                                 coreLoader = coreLoader,
                                 classPath = classPath,
                                 libDirectories = libDirectories) { name, packagePrefix, force ->
    if (force) {
      false
    }
    else {
      !name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier") && !name.startsWith(customPackage)
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
      return@ResolveScopeManager false
    }

    for (prefix in contentPackagePrefixes) {
      if (name.startsWith(prefix)) {
        log.error("Class $name must be not requested from main classloader of $pluginId plugin")
        return@ResolveScopeManager true
      }
    }

    for (prefix in dependencyPackagePrefixes) {
      if (name.startsWith(prefix)) {
        return@ResolveScopeManager true
      }
    }

    false
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
    val packagePrefix = (pluginSet.findEnabledModule(item.name) ?: continue).requireDescriptor().packagePrefix
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

  // force flag is ignored for module - e.g. RailsViewLineMarkerProvider is referenced as extension implementation in several modules
  return PluginClassLoader.ResolveScopeManager { name, packagePrefix, _ ->
    if (name.startsWith(packagePrefix!!) || name.startsWith("com.intellij.ultimate.PluginVerifier")) {
      return@ResolveScopeManager false
    }

    // for a module, the referenced module doesn't have own classloader and is added directly to classpath,
    // so, if name doesn't pass standard package prefix filter,
    // check that it is not in content - if in content, then it means that class is not alien
    for (prefix in packagePrefixes) {
      if (name.startsWith(prefix)) {
        return@ResolveScopeManager false
      }
    }
    true
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
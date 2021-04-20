// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.lang.ClassPath
import com.intellij.util.lang.UrlClassLoader
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*

@ApiStatus.Internal
class ClassLoaderConfigurator(
  private val usePluginClassLoader: Boolean /* grab classes from platform loader only if nothing is found in any of plugin dependencies */,
  private val coreLoader: ClassLoader,
  val idMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  private val additionalLayoutMap: Map<String, Array<String>>) {

  private var javaDep: Optional<IdeaPluginDescriptorImpl>? = null

  // temporary set to produce arrays (avoid allocation for each plugin)
  // set to remove duplicated classloaders
  private val loaders = LinkedHashSet<ClassLoader>()

  // temporary list to produce arrays (avoid allocation for each plugin)
  private val packagePrefixes = ArrayList<String>()
  private val hasAllModules = idMap.containsKey(PluginManagerCore.ALL_MODULES_MARKER)
  private val urlClassLoaderBuilder = UrlClassLoader.build().useCache()

  // todo for dynamic reload this guard doesn't contain all used plugin prefixes
  private val pluginPackagePrefixUniqueGuard = HashSet<String>()
  @Suppress("JoinDeclarationAndAssignment")
  private val resourceFileFactory: ClassPath.ResourceFileFactory?

  init {
    resourceFileFactory = try {
      MethodHandles.lookup().findStatic(coreLoader.loadClass("com.intellij.util.lang.PathClassLoader"), "getResourceFileFactory",
                                        MethodType.methodType(ClassPath.ResourceFileFactory::class.java))
        .invokeExact() as ClassPath.ResourceFileFactory
    }
    catch (ignore: ClassNotFoundException) {
      null
    }
    catch (e: Throwable) {
      log.error(e)
      null
    }
  }

  fun configureDependenciesIfNeeded(mainToSub: Map<IdeaPluginDescriptorImpl, List<IdeaPluginDescriptorImpl>>,
                                    dependencyPlugin: IdeaPluginDescriptorImpl) {
    for ((mainDependent, value) in mainToSub) {
      val mainDependentClassLoader = mainDependent.classLoader as PluginClassLoader
      if (isClassloaderPerDescriptorEnabled(mainDependent)) {
        for (dependency in mainDependent.pluginDependencies!!) {
          urlClassLoaderBuilder.files(mainDependentClassLoader.files)
          for (subDescriptor in value) {
            if (subDescriptor === dependency.subDescriptor) {
              configureSubPlugin(dependency, mainDependentClassLoader, mainDependent)
              break
            }
          }
        }
      }
      else {
        mainDependentClassLoader.attachParent(dependencyPlugin.classLoader!!)
        for (subDescriptor in value) {
          subDescriptor.classLoader = mainDependentClassLoader
        }
      }
    }
    loaders.clear()
    urlClassLoaderBuilder.files(emptyList())
  }

  fun configure(mainDependent: IdeaPluginDescriptorImpl) {
    val pluginPackagePrefix = mainDependent.packagePrefix
    if (pluginPackagePrefix != null && !pluginPackagePrefixUniqueGuard.add(pluginPackagePrefix)) {
      throw PluginException("Package prefix $pluginPackagePrefix is already used", mainDependent.pluginId)
    }

    if (mainDependent.pluginId == PluginManagerCore.CORE_ID || mainDependent.isUseCoreClassLoader) {
      setPluginClassLoaderForMainAndSubPlugins(mainDependent, coreLoader)
      return
    }
    else if (!usePluginClassLoader) {
      setPluginClassLoaderForMainAndSubPlugins(mainDependent, null)
    }
    loaders.clear()

    // first, set class loader for main descriptor
    if (hasAllModules) {
      val implicitDependency = PluginManagerCore.getImplicitDependency(mainDependent) {
        // first, set class loader for main descriptor
        if (javaDep == null) {
          javaDep = Optional.ofNullable(idMap.get(PluginManagerCore.JAVA_PLUGIN_ID))
        }
        javaDep!!.orElse(null)
      }
      implicitDependency?.let { addLoaderOrLogError(mainDependent, it, loaders) }
    }

    var classPath = mainDependent.jarFiles
    if (classPath == null) {
      classPath = collectClassPath(mainDependent)
    }
    else {
      mainDependent.jarFiles = null
    }
    urlClassLoaderBuilder.files(classPath)

    val pluginDependencies = mainDependent.pluginDependencies
    if (pluginDependencies == null) {
      assert(!mainDependent.isUseIdeaClassLoader)
      mainDependent.classLoader = createPluginClassLoader(mainDependent)
      return
    }

    for (dependency in pluginDependencies) {
      if (!dependency.isDisabledOrBroken && (!isClassloaderPerDescriptorEnabled(mainDependent) || dependency.subDescriptor == null)) {
        addClassloaderIfDependencyEnabled(dependency.id, mainDependent)
      }
    }

    // new format
    for (dependency in mainDependent.dependencyDescriptor.plugins) {
      addClassloaderIfDependencyEnabled(dependency.id, mainDependent)
    }

    val mainDependentClassLoader = if (mainDependent.isUseIdeaClassLoader) {
      configureUsingIdeaClassloader(classPath, mainDependent)
    }
    else {
      createPluginClassLoader(mainDependent)
    }

    // second, set class loaders for sub descriptors
    if (usePluginClassLoader && isClassloaderPerDescriptorEnabled(mainDependent)) {
      mainDependent.classLoader = mainDependentClassLoader
      for (dependencyInfo in pluginDependencies) {
        configureSubPlugin(dependencyInfo, mainDependentClassLoader, mainDependent)
      }
    }
    else {
      setPluginClassLoaderForMainAndSubPlugins(mainDependent, mainDependentClassLoader)
    }

    // reset to ensure that stalled data will be not reused somehow later
    loaders.clear()
    urlClassLoaderBuilder.files(emptyList())
  }

  private fun createPluginClassLoader(descriptor: IdeaPluginDescriptorImpl): PluginClassLoader {
    val parentLoaders = if (loaders.isEmpty()) PluginClassLoader.EMPTY_CLASS_LOADER_ARRAY
    else loaders.toArray(PluginClassLoader.EMPTY_CLASS_LOADER_ARRAY)
    return createPluginClassLoader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory)
  }

  private fun configureSubPlugin(dependencyInfo: PluginDependency,
                                 mainDependentClassLoader: ClassLoader,
                                 parentDescriptor: IdeaPluginDescriptorImpl) {
    val dependent = (if (dependencyInfo.isDisabledOrBroken) null else dependencyInfo.subDescriptor) ?: return
    assert(!dependent.isUseIdeaClassLoader)
    val pluginPackagePrefix = dependent.packagePrefix
    if (pluginPackagePrefix == null) {
      if (parentDescriptor.packagePrefix != null) {
        throw PluginException(
          "Sub descriptor must specify package if it is specified for main plugin descriptor " +
          "(descriptorFile=" + dependent.descriptorPath + ")", parentDescriptor.id)
      }
    }
    else {
      if (pluginPackagePrefix == parentDescriptor.packagePrefix) {
        throw PluginException("Sub descriptor must not specify the same package as main plugin descriptor", parentDescriptor.id)
      }

      if (parentDescriptor.packagePrefix == null) {
        val parentId = parentDescriptor.id!!.idString
        if (!(parentId == "Docker" ||
              parentId == "org.jetbrains.plugins.ruby" ||
              parentId == "org.intellij.grails" ||
              parentId == "JavaScript")) {
          throw PluginException("Sub descriptor must not specify package if one is not specified for main plugin descriptor",
                                parentDescriptor.id)
        }
      }
      if (!pluginPackagePrefixUniqueGuard.add(pluginPackagePrefix)) {
        throw PluginException("Package prefix $pluginPackagePrefix is already used", parentDescriptor.id)
      }
    }

    val dependency = idMap.get(dependencyInfo.id)
    if (dependency == null || !dependency.isEnabled) {
      return
    }

    if (pluginPackagePrefix == null) {
      packagePrefixes.clear()
      collectPackagePrefixes(dependent, packagePrefixes)
      // no package prefixes if only bean extension points are configured
      if (packagePrefixes.isEmpty()) {
        log.debug(
          "Optional descriptor $dependencyInfo contains only bean extension points or light services")
      }
    }
    loaders.clear()

    // must be before main descriptor classloader
    // only first level is supported - N level is not supported for a new model (several requirements maybe specified instead)
    if (parentDescriptor.descriptorPath == null) {
      addSiblingClassloaderIfNeeded(dependent, parentDescriptor)
    }

    // add main descriptor classloader as parent
    loaders.add(mainDependentClassLoader)
    addLoaderOrLogError(dependent, dependency, loaders)
    val pluginDependencies = dependent.pluginDependencies

    // add config-less dependencies to classloader parents
    if (pluginDependencies != null) {
      for (subDependency in pluginDependencies) {
        if (!subDependency.isDisabledOrBroken && subDependency.subDescriptor == null) {
          addClassloaderIfDependencyEnabled(subDependency.id, dependent)
        }
      }
    }
    val subClassloader = if (pluginPackagePrefix == null) {
      SubPluginClassLoader(dependent,
        urlClassLoaderBuilder,
        loaders.toTypedArray(),
        packagePrefixes.toTypedArray(),
        coreLoader, resourceFileFactory)
    }
    else {
      createPluginClassLoader(dependent)
    }

    dependent.classLoader = subClassloader
    if (pluginDependencies != null) {
      for (subDependency in pluginDependencies) {
        configureSubPlugin(subDependency, subClassloader, dependent)
      }
    }
  }

  private fun addSiblingClassloaderIfNeeded(dependent: IdeaPluginDescriptorImpl, parentDescriptor: IdeaPluginDescriptorImpl) {
    if (!ClassLoaderConfigurationData.SEPARATE_CLASSLOADER_FOR_SUB) {
      return
    }

    for (dependentModuleDependency in dependent.dependencyDescriptor.modules) {
      if (parentDescriptor.contentDescriptor.findModuleByName(dependentModuleDependency.name) == null) {
        // todo what about dependency on a module that contained in another plugin?
        throw PluginException(
          "Main descriptor $parentDescriptor must list module in content if it is specified as dependency in sub descriptor " +
          "(descriptorFile=${dependent.descriptorPath})", parentDescriptor.id
        )
      }
      for (dependencyPluginDependency in parentDescriptor.pluginDependencies!!) {
        if (!dependencyPluginDependency.isDisabledOrBroken && dependencyPluginDependency.subDescriptor != null &&
            dependentModuleDependency.packageName == dependencyPluginDependency.subDescriptor!!.packagePrefix) {
          val classLoader = dependencyPluginDependency.subDescriptor!!.classLoader
                            ?: throw PluginException("Classloader is null for sibling. " +
                                                     "Please ensure that content entry in the main plugin specifies module with package `" +
                                                     dependentModuleDependency.packageName +
                                                     "` before module with package `${dependent.packagePrefix}`" +
                                                     "(descriptorFile=${dependent.descriptorPath})", parentDescriptor.id)
          loaders.add(classLoader)
        }
      }
    }
  }

  private fun addClassloaderIfDependencyEnabled(dependencyId: PluginId, dependent: IdeaPluginDescriptorImpl) {
    val dependency = idMap.get(dependencyId) ?: return

    // must be first to ensure that it is used first to search classes (very important if main plugin descriptor doesn't have package prefix)
    // check dependencies between optional descriptors (aka modules in a new model) from different plugins
    if (ClassLoaderConfigurationData.SEPARATE_CLASSLOADER_FOR_SUB && dependency.pluginDependencies != null) {
      for (dependentModuleDependency in dependent.dependencyDescriptor.modules) {
        if (dependency.contentDescriptor.findModuleByName(dependentModuleDependency.name) != null) {
          for (pluginDependency in dependency.pluginDependencies!!) {
            if (!pluginDependency.isDisabledOrBroken && pluginDependency.subDescriptor != null &&
                dependentModuleDependency.packageName == pluginDependency.subDescriptor!!.packagePrefix) {
              loaders.add(pluginDependency.subDescriptor!!.classLoader!!)
            }
          }
          break
        }
      }
    }

    val loader = dependency.classLoader
    if (loader == null) {
      log.error(PluginLoadingError.formatErrorMessage(dependent, "requires missing class loader for '${dependency.getName()}'"))
    }
    else if (loader !== coreLoader) {
      loaders.add(loader)
    }
  }

  private fun addLoaderOrLogError(dependent: IdeaPluginDescriptorImpl,
                                  dependency: IdeaPluginDescriptorImpl,
                                  loaders: MutableCollection<ClassLoader>) {
    val loader = dependency.classLoader
    if (loader == null) {
      log.error(PluginLoadingError.formatErrorMessage(dependent, "requires missing class loader for '${dependency.getName()}'"))
    }
    else if (loader !== coreLoader) {
      loaders.add(loader)
    }
  }

  private fun setPluginClassLoaderForMainAndSubPlugins(rootDescriptor: IdeaPluginDescriptorImpl, classLoader: ClassLoader?) {
    rootDescriptor.classLoader = classLoader
    for (dependency in rootDescriptor.getPluginDependencies()) {
      if (dependency.subDescriptor != null) {
        val descriptor = idMap.get(dependency.id)
        if (descriptor != null && descriptor.isEnabled) {
          setPluginClassLoaderForMainAndSubPlugins(dependency.subDescriptor!!, classLoader)
        }
      }
    }
  }

  private fun collectClassPath(descriptor: IdeaPluginDescriptorImpl): List<Path> {
    val pluginPath = descriptor.path
    if (!Files.isDirectory(pluginPath)) {
      return listOf(pluginPath)
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
        val moduleName = pluginPath.fileName.toString()
        val additionalPaths = additionalLayoutMap.get(moduleName)
        if (additionalPaths != null) {
          for (path in additionalPaths) {
            result.add(productionDirectory.resolve(path))
          }
        }
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

// this list doesn't duplicate of PluginXmlFactory.CLASS_NAMES - interface related must be not here
private val IMPL_CLASS_NAMES = ReferenceOpenHashSet(arrayOf(
  "implementation", "implementationClass", "builderClass",
  "serviceImplementation", "class", "className",
  "instance", "implementation-class"))

// do not use class reference here
@Suppress("SSBasedInspection")
private val log: Logger
  get() = Logger.getInstance("#com.intellij.ide.plugins.PluginManager")

// static to ensure that anonymous classes will not hold ClassLoaderConfigurator
private fun createPluginClassLoader(parentLoaders: Array<ClassLoader>,
                                    descriptor: IdeaPluginDescriptorImpl,
                                    urlClassLoaderBuilder: UrlClassLoader.Builder,
                                    coreLoader: ClassLoader,
                                    resourceFileFactory: ClassPath.ResourceFileFactory?): PluginClassLoader {
  // main plugin descriptor
  if (descriptor.descriptorPath == null) {
    when (descriptor.id!!.idString) {
      "com.intellij.diagram" -> {
        // multiple packages - intellij.diagram and intellij.diagram.impl modules
        return createPluginClassLoaderWithExtraPackage(parentLoaders = parentLoaders,
                                                       descriptor = descriptor,
                                                       urlClassLoaderBuilder = urlClassLoaderBuilder,
                                                       coreLoader = coreLoader,
                                                       resourceFileFactory = resourceFileFactory,
                                                       customPackage = "com.intellij.diagram.")
      }
      "com.intellij.struts2" -> {
        return createPluginClassLoaderWithExtraPackage(parentLoaders = parentLoaders,
                                                       descriptor = descriptor,
                                                       urlClassLoaderBuilder = urlClassLoaderBuilder,
                                                       coreLoader = coreLoader,
                                                       resourceFileFactory = resourceFileFactory,
                                                       customPackage = "com.intellij.lang.ognl.")
      }
      "com.intellij.properties" -> {
        // todo ability to customize (cannot move due to backward compatibility)
        return createPluginClassloader(parentLoaders = parentLoaders,
                                       descriptor = descriptor,
                                       urlClassLoaderBuilder = urlClassLoaderBuilder,
                                       coreLoader = coreLoader,
                                       resourceFileFactory = resourceFileFactory) { name, packagePrefix, force ->
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

    if (descriptor.packagePrefix == null) {
      return PluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor, descriptor.pluginPath, coreLoader, null, null,
                               resourceFileFactory)
    }
  }
  else {
    if (!descriptor.contentDescriptor.modules.isEmpty()) {
      // see "The `content.module` element" section about content handling for a module
      return createPluginClassloader(parentLoaders = parentLoaders,
                                     descriptor = descriptor,
                                     urlClassLoaderBuilder = urlClassLoaderBuilder,
                                     coreLoader = coreLoader,
                                     resourceFileFactory = resourceFileFactory,
                                     resolveScopeManager = createModuleContentBasedScope(descriptor))
    }
    else if (descriptor.packagePrefix != null) {
      return createPluginClassloader(parentLoaders = parentLoaders,
                                     descriptor = descriptor,
                                     urlClassLoaderBuilder = urlClassLoaderBuilder,
                                     coreLoader = coreLoader,
                                     resourceFileFactory = resourceFileFactory) { name, packagePrefix, _ ->
        // force flag is ignored for module - e.g. RailsViewLineMarkerProvider is referenced
        // as extension implementation in several modules
        !name.startsWith(packagePrefix) && !name.startsWith("com.intellij.ultimate.PluginVerifier")
      }
    }
  }

  return createPluginClassloader(parentLoaders, descriptor, urlClassLoaderBuilder, coreLoader, resourceFileFactory,
                                 createPluginDependencyAndContentBasedScope(descriptor))
}

private fun createPluginClassloader(parentLoaders: Array<ClassLoader>,
                                    descriptor: IdeaPluginDescriptorImpl,
                                    urlClassLoaderBuilder: UrlClassLoader.Builder,
                                    coreLoader: ClassLoader,
                                    resourceFileFactory: ClassPath.ResourceFileFactory?,
                                    resolveScopeManager: PluginClassLoader.ResolveScopeManager?): PluginClassLoader {
  return PluginClassLoader(urlClassLoaderBuilder, parentLoaders, descriptor, descriptor.pluginPath, coreLoader,
                           resolveScopeManager, descriptor.packagePrefix, resourceFileFactory)
}

private fun createPluginClassLoaderWithExtraPackage(parentLoaders: Array<ClassLoader>,
                                                    descriptor: IdeaPluginDescriptorImpl,
                                                    urlClassLoaderBuilder: UrlClassLoader.Builder,
                                                    coreLoader: ClassLoader,
                                                    resourceFileFactory: ClassPath.ResourceFileFactory?,
                                                    customPackage: String): PluginClassLoader {
  return createPluginClassloader(parentLoaders = parentLoaders,
                                 descriptor = descriptor,
                                 urlClassLoaderBuilder = urlClassLoaderBuilder,
                                 coreLoader = coreLoader,
                                 resourceFileFactory = resourceFileFactory) { name, packagePrefix, force ->
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
private fun createPluginDependencyAndContentBasedScope(descriptor: IdeaPluginDescriptorImpl): PluginClassLoader.ResolveScopeManager {
  val contentPackagePrefixes = getContentPackagePrefixes(descriptor)
  val dependencyPackagePrefixes = getDependencyPackagePrefixes(descriptor)
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
  var result: MutableList<String>? = null
  for (item in descriptor.contentDescriptor.modules) {
    if (item.isInjected) {
      continue
    }

    val packagePrefix = item.packageName ?: continue
    if (result == null) {
      result = ArrayList(descriptor.contentDescriptor.modules.size)
    }
    result.add("$packagePrefix.")
  }
  return result ?: emptyList()
}

private fun getDependencyPackagePrefixes(descriptor: IdeaPluginDescriptorImpl): List<String> {
  if (descriptor.dependencyDescriptor.modules.isEmpty()) {
    return emptyList()
  }

  val result = ArrayList<String>(descriptor.dependencyDescriptor.modules.size)
  for (item in descriptor.dependencyDescriptor.modules) {
    val packagePrefix = item.packageName
    // intellij.platform.commercial.verifier is injected
    if (packagePrefix != null && item.name != "intellij.platform.commercial.verifier") {
      result.add("$packagePrefix.")
    }
  }
  return result
}

private fun createModuleContentBasedScope(descriptor: IdeaPluginDescriptorImpl): PluginClassLoader.ResolveScopeManager {
  val packagePrefixes = ArrayList<String>(descriptor.contentDescriptor.modules.size)
  for (item in descriptor.contentDescriptor.modules) {
    item.packageName?.let {
      packagePrefixes.add("$it.")
    }
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

private fun isClassloaderPerDescriptorEnabled(descriptor: IdeaPluginDescriptorImpl): Boolean {
  return ClassLoaderConfigurationData.isClassloaderPerDescriptorEnabled(descriptor.id!!, descriptor.packagePrefix)
}

private fun collectPackagePrefixes(dependent: IdeaPluginDescriptorImpl, packagePrefixes: MutableList<String>) {
  // from extensions
  dependent.unsortedEpNameToExtensionElements.values.forEach { elements ->
    for (element in elements) {
      if (!element.hasAttributes()) {
        continue
      }
      for (attributeName in IMPL_CLASS_NAMES) {
        val className = element.getAttributeValue(attributeName)
        if (className != null && !className.isEmpty()) {
          addPackageByClassNameIfNeeded(className, packagePrefixes)
          break
        }
      }
    }
  }

  // from services
  collectFromServices(dependent.appContainerDescriptor, packagePrefixes)
  collectFromServices(dependent.projectContainerDescriptor, packagePrefixes)
  collectFromServices(dependent.moduleContainerDescriptor, packagePrefixes)
}

private fun addPackageByClassNameIfNeeded(name: String, packagePrefixes: MutableList<String>) {
  for (packagePrefix in packagePrefixes) {
    if (name.startsWith(packagePrefix)) {
      return
    }
  }

  // for classes like com.intellij.thymeleaf.lang.ThymeleafParserDefinition$SPRING_SECURITY_EXPRESSIONS
  // we must not try to load the containing package
  if (name.indexOf('$') != -1) {
    packagePrefixes.add(name)
    return
  }

  val lastPackageDot = name.lastIndexOf('.')
  if (lastPackageDot > 0 && lastPackageDot != name.length) {
    addPackagePrefixIfNeeded(packagePrefixes, name.substring(0, lastPackageDot + 1))
  }
}

private fun addPackagePrefixIfNeeded(packagePrefixes: MutableList<String>, packagePrefix: String) {
  for (i in packagePrefixes.indices) {
    val existingPackagePrefix = packagePrefixes.get(i)
    if (packagePrefix.startsWith(existingPackagePrefix)) {
      return
    }
    else if (existingPackagePrefix.startsWith(packagePrefix) && existingPackagePrefix.indexOf('$') == -1) {
      packagePrefixes.set(i, packagePrefix)
      for (j in packagePrefixes.size - 1 downTo i + 1) {
        if (packagePrefixes.get(j).startsWith(packagePrefix)) {
          packagePrefixes.removeAt(j)
        }
      }
      return
    }
  }
  packagePrefixes.add(packagePrefix)
}

private fun collectFromServices(containerDescriptor: ContainerDescriptor, packagePrefixes: MutableList<String>) {
  for (service in (containerDescriptor.services ?: return)) {
    // testServiceImplementation is ignored by intention
    service.serviceImplementation?.let {
      addPackageByClassNameIfNeeded(it, packagePrefixes)
    }
    service.headlessImplementation?.let {
      addPackageByClassNameIfNeeded(it, packagePrefixes)
    }
  }
}

private fun configureUsingIdeaClassloader(classPath: List<Path?>, descriptor: IdeaPluginDescriptorImpl): ClassLoader {
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
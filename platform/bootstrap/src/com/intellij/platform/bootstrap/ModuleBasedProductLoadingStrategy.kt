// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.ClassPathXmlPathResolver
import com.intellij.ide.plugins.DiscoveredPluginsList
import com.intellij.ide.plugins.ImmutableZipFileDataLoader
import com.intellij.ide.plugins.LocalFsDataLoader
import com.intellij.ide.plugins.ModuleLoadingRule
import com.intellij.ide.plugins.PluginDescriptorLoadingContext
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.PluginXmlPathResolver
import com.intellij.ide.plugins.PluginsSourceContext
import com.intellij.ide.plugins.ProductLoadingStrategy
import com.intellij.ide.plugins.deprecatedLoadCorePluginForModuleBasedLoader
import com.intellij.ide.plugins.loadDescriptorFromDir
import com.intellij.ide.plugins.loadDescriptorFromFileOrDir
import com.intellij.ide.plugins.loadDescriptorFromJar
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.pluginSystem.parser.impl.consume
import com.intellij.platform.runtime.product.PluginModuleGroup
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.impl.ServiceModuleMapping
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.ZipEntryResolverPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension

internal class ModuleBasedProductLoadingStrategy(internal val moduleRepository: RuntimeModuleRepository) : ProductLoadingStrategy() {
  private val currentMode by lazy {
    val currentModeId = System.getProperty(PLATFORM_PRODUCT_MODE_PROPERTY, ProductMode.MONOLITH.id)
    val currentMode = ProductMode.findById(currentModeId)
    if (currentMode == null) {
      error("Unknown mode '$currentModeId' specified in '$PLATFORM_PRODUCT_MODE_PROPERTY' system property")
    }
    currentMode
  }
  
  private val productModules by lazy {
    val rootModuleId = System.getProperty(PLATFORM_ROOT_MODULE_PROPERTY)
    if (rootModuleId == null) {
      error("'$PLATFORM_ROOT_MODULE_PROPERTY' system property is not specified")
    }

    val rootModule = moduleRepository.getModule(RuntimeModuleId.module(rootModuleId))
    val productModulesPath = "META-INF/$rootModuleId/product-modules.xml"
    val moduleGroupStream = rootModule.readFile(productModulesPath)
    if (moduleGroupStream == null) {
      error("$productModulesPath is not found in '$rootModuleId' module")
    }
    ProductModulesSerialization.loadProductModules(moduleGroupStream, productModulesPath, currentMode, moduleRepository)
  }
  
  override val currentModeId: String
    get() = currentMode.id

  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
    fun collectDependencies(module: RuntimeModuleDescriptor, result: MutableSet<RuntimeModuleDescriptor>) {
      if (result.add(module)) {
        module.dependencies.forEach { collectDependencies(it, result) }
      }
    }
    
    val embeddedModulesWithDependencies = LinkedHashSet<RuntimeModuleDescriptor>()
    for (module in productModules.mainModuleGroup.includedModules) {
      if (module.loadingRule == RuntimeModuleLoadingRule.EMBEDDED) {
        collectDependencies(module.moduleDescriptor, embeddedModulesWithDependencies)
      }
    }
    val mainGroupClassPath = embeddedModulesWithDependencies.flatMapTo(LinkedHashSet()) { it.resourceRootPaths }
    val classPath = (bootstrapClassLoader as PathClassLoader).classPath
    logger<ModuleBasedProductLoadingStrategy>().info("New classpath roots:\n${(mainGroupClassPath - classPath.baseUrls.toSet()).joinToString("\n")}")
    classPath.addFiles(mainGroupClassPath)
  }

  override fun loadPluginDescriptors(
    scope: CoroutineScope,
    loadingContext: PluginDescriptorLoadingContext,
    customPluginDir: Path,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    isInDevServerMode: Boolean,
    isRunningFromSources: Boolean,
    zipPool: ZipEntryResolverPool,
    mainClassLoader: ClassLoader,
  ): Deferred<List<DiscoveredPluginsList>> {
    val platformPrefix = PlatformUtils.getPlatformPrefix()
    val isRunningFromSourcesWithoutDevBuild = isRunningFromSources && !isInDevServerMode
    val classpathPathResolver = ClassPathXmlPathResolver(
      classLoader = mainClassLoader,
      isRunningFromSourcesWithoutDevBuild = isRunningFromSourcesWithoutDevBuild,
      isOptionalProductModule = { moduleId -> this@ModuleBasedProductLoadingStrategy.isOptionalProductModule(moduleId) },
    )
    val useCoreClassLoader = platformPrefix.startsWith("CodeServer") || java.lang.Boolean.getBoolean("idea.force.use.core.classloader")
    val pathResolver = if (isRunningFromSourcesWithoutDevBuild) {
      RunningFromSourceModuleBasedPathResolver(moduleRepository, fallbackResolver = classpathPathResolver)
    }
    else {
      classpathPathResolver
    }
    val corePlugin = scope.async(Dispatchers.IO) {
      deprecatedLoadCorePluginForModuleBasedLoader(
        platformPrefix = platformPrefix,
        isInDevServerMode = isInDevServerMode,
        isUnitTestMode = isUnitTestMode,
        isRunningFromSources = isRunningFromSources,
        loadingContext = loadingContext,
        pathResolver = pathResolver,
        useCoreClassLoader = useCoreClassLoader,
        classLoader = mainClassLoader,
        jarFileForModule = { moduleId, _ -> findProductContentModuleClassesRoot(moduleId) },
        pool = zipPool,
      )
    }
    val custom = loadCustomPluginDescriptors(scope, customPluginDir, loadingContext, zipPool)
    val bundled = loadBundledPluginDescriptors(scope, loadingContext, zipPool)
    return scope.async {
      listOfNotNull(
        corePlugin.await()?.let { DiscoveredPluginsList(listOf(it), PluginsSourceContext.Product) },
        custom.await(),
        bundled.await(),
      )
    }
  }

  private fun loadBundledPluginDescriptors(
    scope: CoroutineScope,
    context: PluginDescriptorLoadingContext,
    zipFilePool: ZipEntryResolverPool,
  ): Deferred<DiscoveredPluginsList> {
    val mainGroupModulesSet = productModules.mainModuleGroup.includedModules.mapTo(HashSet()) { it.moduleDescriptor.moduleId }
    val mainGroupResourceRootSet = productModules.mainModuleGroup.includedModules.flatMapTo(HashSet()) { it.moduleDescriptor.resourceRootPaths }
    val serviceModuleMappingDeferred = scope.async { 
      ServiceModuleMapping.buildMapping(productModules)
    }

    productModules.notLoadedBundledPluginModules.forEach { (notLoadedId, failedDependencyPath) ->
      // todo: convert this to an error after fixing the problem with intellij.performanceTesting.async plugin: IJPL-186414
      logger<ModuleBasedProductLoadingStrategy>().warn("""
        |Bundled plugin module '${notLoadedId.presentableName}' couldn't be loaded because of missing dependency:
        |${failedDependencyPath.joinToString(" -> ") { it.presentableName }}
        |""".trimMargin())
    }

    val bundled = productModules.bundledPluginModuleGroups.map { moduleGroup ->
      scope.async {
        if (moduleGroup.includedModules.none { it.moduleDescriptor.moduleId in mainGroupModulesSet } || isPluginWithUseIdeaClassLoader(moduleGroup, context, zipFilePool)) {
          val serviceModuleMapping = serviceModuleMappingDeferred.await()
          loadPluginDescriptorFromRuntimeModule(
            pluginModuleGroup = moduleGroup,
            context = context,
            zipFilePool = zipFilePool,
            serviceModuleMapping = serviceModuleMapping,
            mainGroupResourceRootSet = mainGroupResourceRootSet,
            isBundled = true,
            pluginDir = null,
          )
        }
        else {
          /* todo: intellij.performanceTesting.async plugin has different distributions for different IDEs, in some IDEs it has dependencies 
             on 'intellij.profiler.common' and other module from the platform, in other IDEs it includes them as its own content. In the
             latter case we currently cannot run it using the modular loader, because these modules will be loaded twice.
             Remove this check after IJPL-186414 is fixed */
          logger<ModuleBasedProductLoadingStrategy>().info("Skipped loading $moduleGroup because it intersects with main module group")
          null
        }
      }
    }
    return scope.async { DiscoveredPluginsList(bundled.awaitAll().filterNotNull(), PluginsSourceContext.Bundled) }
  }

  /**
   * Returns true if [pluginModuleGroup] is a plugin with `use-idea-classloader` which should be loaded by platform.
   * Content modules of these plugins always should be loaded.
   */
  private fun isPluginWithUseIdeaClassLoader(
    pluginModuleGroup: PluginModuleGroup,
    loadingContext: PluginDescriptorLoadingContext,
    zipFilePool: ZipEntryResolverPool,
  ): Boolean {
    val mainResourceRoot = pluginModuleGroup.mainModule.resourceRootPaths.singleOrNull() ?: return false
    val input = readMainModulePluginXml(mainResourceRoot, zipFilePool, pluginModuleGroup) ?: return false
    // TODO: do we need to support xIncludes in this case?
    @Suppress("TestOnlyProblems")
    val rawDescriptor = PluginDescriptorFromXmlStreamConsumer(loadingContext.readContext, xIncludeLoader = null).let {
      it.consume(input, mainResourceRoot.toString())
      it.build()
    }
    return rawDescriptor.isUseIdeaClassLoader
  }

  private fun readMainModulePluginXml(
    mainResourceRoot: Path,
    zipFilePool: ZipEntryResolverPool,
    pluginModuleGroup: PluginModuleGroup,
  ): ByteArray? {
    if (Files.isDirectory(mainResourceRoot)) {
      val dataLoader = LocalFsDataLoader(mainResourceRoot)
      return dataLoader.load(path = PluginManagerCore.PLUGIN_XML_PATH, pluginDescriptorSourceOnly = true)
    }
    // mainResourceRoot is a JAR file
    var resolver: ZipEntryResolverPool.EntryResolver? = null
    try {
      resolver = zipFilePool.load(mainResourceRoot)
      val dataLoader = ImmutableZipFileDataLoader(resolver = resolver, zipPath = mainResourceRoot)
      return dataLoader.load(path = PluginManagerCore.PLUGIN_XML_PATH, pluginDescriptorSourceOnly = true)
    }
    catch (e: Throwable) {
      logger<ModuleBasedProductLoadingStrategy>().warn("Failed to load ${PluginManagerCore.PLUGIN_XML_PATH} from '${pluginModuleGroup.mainModule.moduleId.presentableName}' module: $e", e)
      return null
    }
    finally {
      resolver?.close()
    }
  }

  private fun loadCustomPluginDescriptors(
    scope: CoroutineScope,
    customPluginDir: Path,
    context: PluginDescriptorLoadingContext,
    zipFilePool: ZipEntryResolverPool,
  ): Deferred<DiscoveredPluginsList> {
    if (!Files.isDirectory(customPluginDir)) {
      return CompletableDeferred(DiscoveredPluginsList(emptyList(), PluginsSourceContext.Custom))
    }
    val deferredDescriptors = ArrayList<Deferred<PluginMainDescriptor?>>()
    Files.newDirectoryStream(customPluginDir).use { dirStream ->
      val additionalRepositoryPaths = ArrayList<Path>()
      dirStream.forEach { file ->
        val moduleRepository = file.resolve("module-descriptors.jar")
        if (moduleRepository.exists()) {
          additionalRepositoryPaths.add(moduleRepository)
        }
        else {
          deferredDescriptors.add(scope.async {
            loadDescriptorFromFileOrDir(
              file = file,
              loadingContext = context,
              pool = zipFilePool,
            )
          })
        }
      }
    }
    return scope.async { DiscoveredPluginsList(deferredDescriptors.awaitAll().filterNotNull(), PluginsSourceContext.Custom) }
  }

  private fun loadPluginDescriptorFromRuntimeModule(
    pluginModuleGroup: PluginModuleGroup,
    context: PluginDescriptorLoadingContext,
    zipFilePool: ZipEntryResolverPool,
    serviceModuleMapping: ServiceModuleMapping?,
    mainGroupResourceRootSet: Set<Path>,
    isBundled: Boolean,
    pluginDir: Path?,
  ): PluginMainDescriptor? {
    val resourceRoots = pluginModuleGroup.mainModule.resourceRootPaths
    if (resourceRoots.isEmpty()) {
      thisLogger().warn(
        "Main plugin module must have at least one resource root, so '${pluginModuleGroup.mainModule.moduleId.presentableName}' won't be loaded"
      )
      return null
    }

    val includedModules = pluginModuleGroup.includedModules
    val allResourceRoots = includedModules.flatMapTo(LinkedHashSet()) { it.moduleDescriptor.resourceRootPaths }
    val additionalServiceModules = serviceModuleMapping?.getAdditionalModules(pluginModuleGroup) ?: collectRequiredLibraryModules(pluginModuleGroup)
    if (additionalServiceModules.isNotEmpty()) {
      thisLogger().debug { "Additional modules will be added to classpath of $pluginModuleGroup: $additionalServiceModules" }
      additionalServiceModules.flatMapTo(allResourceRoots) {
        // resource roots from plugin modules shouldn't intersect with main group modules, but currently they can: IJPL-671
        it.resourceRootPaths - mainGroupResourceRootSet   
      }
    }
    val allResourceRootsList = allResourceRoots.toList()

    val descriptor = resourceRoots.firstNotNullOfOrNull { resourceRoot ->
      tryLoadingPluginDescriptorFromJarOrDirectory(resourceRoot, allResourceRootsList, zipFilePool,
                                                   pluginModuleGroup, context, isBundled, pluginDir)
    }
    val modulesWithJarFiles = descriptor?.contentModules?.flatMap { moduleItem ->
      val jarFiles = moduleItem.jarFiles
      if (moduleItem.moduleLoadingRule != ModuleLoadingRule.EMBEDDED && jarFiles != null) jarFiles else emptyList()
    }
    descriptor?.jarFiles = allResourceRootsList.filter { modulesWithJarFiles == null || it !in modulesWithJarFiles }
    return descriptor
  }

  private fun tryLoadingPluginDescriptorFromJarOrDirectory(
    resourceRoot: Path,
    allResourceRootsList: List<Path>,
    zipFilePool: ZipEntryResolverPool,
    pluginModuleGroup: PluginModuleGroup,
    context: PluginDescriptorLoadingContext,
    isBundled: Boolean,
    pluginDir: Path?,
  ): PluginMainDescriptor? {
    return if (Files.isDirectory(resourceRoot)) {
      val fallbackResolver = PluginXmlPathResolver(allResourceRootsList.filter { it.extension == "jar" }, zipFilePool)
      val resolver = ModuleBasedPluginXmlPathResolver(
        includedModules = pluginModuleGroup.includedModules,
        optionalModuleIds = pluginModuleGroup.optionalModuleIds,
        notLoadedModuleIds = pluginModuleGroup.notLoadedModuleIds,
        fallbackResolver = fallbackResolver,
      )
      loadDescriptorFromDir(
        dir = resourceRoot,
        loadingContext = context,
        pool = zipFilePool,
        pathResolver = resolver,
        isBundled = isBundled,
        pluginDir = pluginDir,
      )
        .also { descriptor ->
          descriptor?.contentModules?.forEach { module ->
            if (module.packagePrefix == null) {
              val moduleId = module.moduleId
              module.jarFiles = moduleRepository.getModule(RuntimeModuleId.module(moduleId.name)).resourceRootPaths
            }
          }
        }
      }
      else {
      val defaultResolver = PluginXmlPathResolver(allResourceRootsList, zipFilePool)
      val pathResolver = if (allResourceRootsList.size == 1 && pluginModuleGroup.notLoadedModuleIds.isEmpty()) {
        defaultResolver
      }
      else {
        ModuleBasedPluginXmlPathResolver(
          includedModules = pluginModuleGroup.includedModules,
          optionalModuleIds = pluginModuleGroup.optionalModuleIds,
          notLoadedModuleIds = pluginModuleGroup.notLoadedModuleIds,
          fallbackResolver = defaultResolver,
        )
      }
      val pluginDir = pluginDir ?: resourceRoot.parent.parent
      loadDescriptorFromJar(
        file = resourceRoot,
        loadingContext = context,
        pool = zipFilePool,
        pathResolver = pathResolver,
        isBundled = isBundled,
        pluginDir = pluginDir,
      )
    }
  }

  /* TODO: reuse ServiceModuleMapping instead */
  private fun collectRequiredLibraryModules(pluginModuleGroup: PluginModuleGroup): Set<RuntimeModuleDescriptor> {
    val includedInPlatform = productModules.mainModuleGroup.includedModules.mapTo(HashSet()) { it.moduleDescriptor }

    return pluginModuleGroup.includedModules.flatMapTo(LinkedHashSet()) { includedModule ->
      includedModule.moduleDescriptor.dependencies.filter { dependency ->
        dependency.moduleId.stringId.startsWith(RuntimeModuleId.LIB_NAME_PREFIX) && dependency !in includedInPlatform
      }
    }
  }

  override fun isOptionalProductModule(moduleId: String): Boolean {
    return productModules.mainModuleGroup.optionalModuleIds.contains(RuntimeModuleId.module(moduleId))
  }

  override fun findProductContentModuleClassesRoot(moduleId: PluginModuleId, moduleDir: Path): Path? {
    return findProductContentModuleClassesRoot(moduleId)
  }

  private fun findProductContentModuleClassesRoot(moduleId: PluginModuleId): Path? {
    val resolvedModule = moduleRepository.resolveModule(RuntimeModuleId.module(moduleId.name)).resolvedModule
    if (resolvedModule == null) {
      // https://youtrack.jetbrains.com/issue/CPP-38280
      // we log here, as only for JetBrainsClient it is expected that some module is not resolved
      thisLogger().debug("Skip loading product content module $moduleId because its classes root isn't present")
      return null
    }

    val paths = resolvedModule.resourceRootPaths
    val singlePath = paths.singleOrNull()
    val isRunningFromSourcesWithoutDevBuild = PluginManagerCore.isRunningFromSources() && !AppMode.isRunningFromDevBuild()
    /* when running from sources without dev build, resources of a content module may include the module output directory and paths to its
       module-level libraries, so this function may return null so resolveModuleFile and resolveCustomModuleClassesRoots from
       ModuleBasedPluginXmlPathResolver will be used to load the module */
    if (singlePath == null && !isRunningFromSourcesWithoutDevBuild) {
      error("Content modules are supposed to have only one resource root, but $moduleId have multiple: $paths")
    }

    return singlePath
  }
}

private const val PLATFORM_ROOT_MODULE_PROPERTY = "intellij.platform.root.module"
private const val PLATFORM_PRODUCT_MODE_PROPERTY = "intellij.platform.product.mode"

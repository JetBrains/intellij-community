// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.ClassPathXmlPathResolver
import com.intellij.ide.plugins.DiscoveredPluginsList
import com.intellij.ide.plugins.PluginDescriptorLoadingContext
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.PluginXmlPathResolver
import com.intellij.ide.plugins.PluginsSourceContext
import com.intellij.ide.plugins.ProductLoadingStrategy
import com.intellij.ide.plugins.deprecatedLoadCorePluginForModuleBasedLoader
import com.intellij.ide.plugins.loadDescriptorFromDir
import com.intellij.ide.plugins.loadDescriptorFromFileOrDir
import com.intellij.ide.plugins.loadDescriptorFromJar
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.ZipEntryResolverPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

internal class ModuleBasedProductLoadingStrategy(internal val moduleRepository: RuntimeModuleRepository) : ProductLoadingStrategy() {
  private val currentMode: MutableStateFlow<String> by lazy { MutableStateFlow(computeInitialModeId()) }

  override val currentModeId: String
    get() = currentMode.value

  override val currentModeIdFlow: StateFlow<String>
    get() = currentMode

  private val productModules by lazy {
    val rootModuleId = System.getProperty(PLATFORM_ROOT_MODULE_PROPERTY)
    if (rootModuleId == null) {
      error("'$PLATFORM_ROOT_MODULE_PROPERTY' system property is not specified")
    }

    val rootModule = moduleRepository.getModule(RuntimeModuleId.legacyJpsModule(rootModuleId))
    val productModulesPath = "META-INF/$rootModuleId/product-modules.xml"
    val moduleGroupStream = rootModule.readFile(productModulesPath)
    if (moduleGroupStream == null) {
      error("$productModulesPath is not found in '$rootModuleId' module")
    }
    ProductModulesSerialization.loadProductModules(moduleGroupStream, productModulesPath, moduleRepository)
  }

  private fun computeInitialModeId(): String {
    val initialModeId = if (AppMode.isIjLight()) ProductMode.LIGHT.id
                        else System.getProperty(PLATFORM_PRODUCT_MODE_PROPERTY, ProductMode.MONOLITH.id)
    if (ProductMode.findById(initialModeId) == null) {
      error("Unknown mode '$initialModeId' specified in '$PLATFORM_PRODUCT_MODE_PROPERTY' system property")
    }
    return initialModeId
  }

  override fun advanceToLightWithRdConnectionMode(): Boolean {
    return currentMode.compareAndSet(ProductMode.LIGHT.id, ProductMode.LIGHT_WITH_RD_CONNECTION.id)
  }

  override fun advanceToFrontendMode(): Boolean {
    return currentMode.compareAndSet(ProductMode.LIGHT_WITH_RD_CONNECTION.id, ProductMode.FRONTEND.id)
  }

  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
    val logger = logger<ModuleBasedProductLoadingStrategy>()
    val tracing = logger.isTraceEnabled

    val corePluginDescriptorModule = System.getProperty(PLATFORM_CORE_PLUGIN_DESCRIPTOR_MODULE_PROPERTY, "intellij.frontend.split.customization")
    val corePluginHeader = moduleRepository.findBundledPluginHeader(RuntimeModuleId.legacyJpsModule(corePluginDescriptorModule))
    if (corePluginHeader == null) {
      error("The core plugin header is not found in $moduleRepository by module $corePluginDescriptorModule")
    }
    val mainGroupClassPath = corePluginHeader.includedModules.filter { it.loadingRule == RuntimeModuleLoadingRule.EMBEDDED }.flatMapTo(LinkedHashSet()) { module ->
      val classpath = moduleRepository.findModuleHeader(module.moduleId)?.ownClasspath ?: emptyList()
      if (tracing) {
        classpath.forEach { logger.trace("Classpath for core plugin: adding $it from module '${module.moduleId.displayName}'") }
      }
      classpath
    }

    val classPath = (bootstrapClassLoader as PathClassLoader).classPath
    logger.info("New classpath roots:\n${(mainGroupClassPath - classPath.files.toSet()).joinToString("\n")}")
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
    val classpathPathResolver = ClassPathXmlPathResolver(
      classLoader = mainClassLoader,
      isRunningFromSourcesWithoutDevBuild = false,
    )
    val useCoreClassLoader = platformPrefix.startsWith("CodeServer") || java.lang.Boolean.getBoolean("idea.force.use.core.classloader")
    val corePlugin = scope.async(Dispatchers.IO) {
      deprecatedLoadCorePluginForModuleBasedLoader(
        platformPrefix = platformPrefix,
        isInDevServerMode = isInDevServerMode,
        isUnitTestMode = isUnitTestMode,
        isRunningFromSources = isRunningFromSources,
        loadingContext = loadingContext,
        pathResolver = classpathPathResolver,
        useCoreClassLoader = useCoreClassLoader,
        classLoader = mainClassLoader,
        jarFileForModule = { moduleId, _ -> findProductContentModuleClassesRoot(moduleId) },
        pool = zipPool,
      )
    }
    val custom = loadCustomPluginDescriptors(scope, customPluginDir, loadingContext, zipPool)
    val effectiveBundledPluginsDir = bundledPluginDir ?: PathManager.getBundledPluginsDir()
    val bundled = loadBundledPluginsFromPluginHeaders(scope, effectiveBundledPluginsDir, loadingContext, zipPool)
    return scope.async {
      listOfNotNull(
        corePlugin.await()?.let { DiscoveredPluginsList(listOf(it), PluginsSourceContext.Product) },
        custom.await(),
        bundled.await(),
      )
    }
  }

  private fun loadBundledPluginsFromPluginHeaders(
    scope: CoroutineScope,
    bundledPluginsDir: Path,
    loadingContext: PluginDescriptorLoadingContext,
    zipPool: ZipEntryResolverPool,
  ): Deferred<DiscoveredPluginsList> {
    val bundled = productModules.bundledPluginDescriptorModules.mapNotNull { pluginDescriptorModuleId ->
      val pluginHeader = moduleRepository.findBundledPluginHeader(pluginDescriptorModuleId)
      if (pluginHeader == null) {
        logger<ModuleBasedProductLoadingStrategy>().error("Plugin header for module '${pluginDescriptorModuleId.displayName}' is not found in the runtime module repository")
        return@mapNotNull null
      }
      scope.async {
        loadBundledPluginFromPluginHeader(pluginHeader, bundledPluginsDir, zipPool, loadingContext)
      }
    }
    return scope.async { DiscoveredPluginsList(bundled.awaitAll().filterNotNull(), PluginsSourceContext.Bundled) }
  }

  private fun loadBundledPluginFromPluginHeader(
    pluginHeader: RuntimePluginHeader,
    bundledPluginsDir: Path,
    zipPool: ZipEntryResolverPool,
    loadingContext: PluginDescriptorLoadingContext,
  ) : PluginMainDescriptor? {
    val pluginDescriptorClasspathSet = LinkedHashSet<Path>()
    val logger = thisLogger()
    val traceLogging = logger.isTraceEnabled
    for (includedModule in pluginHeader.includedModules) {
      if (includedModule.loadingRule == RuntimeModuleLoadingRule.EMBEDDED) {
        val header = moduleRepository.findModuleHeader(includedModule.moduleId)
        if (header == null) {
          logger.error("Module '${includedModule.moduleId}' included as embedded in the header of plugin '${pluginHeader.pluginId}' is not found in the module repository")
          continue
        }
        if (traceLogging) {
          for (path in header.ownClasspath) {
            logger.info("Classpath for '${pluginHeader.pluginId}': adding $path from module '${includedModule.moduleId.displayName}'")
          }
        }
        pluginDescriptorClasspathSet.addAll(header.ownClasspath)
      }
    }
    val pluginDescriptorClasspath = pluginDescriptorClasspathSet.toList()
    val pluginDescriptorModuleHeader = moduleRepository.findModuleHeader(pluginHeader.pluginDescriptorModuleId)
    if (pluginDescriptorModuleHeader == null) {
      logger.error("Plugin descriptor module for '${pluginHeader.pluginDescriptorModuleId}' is not found in the module repository")
      return null
    }
    val pluginDescriptorOwnClasspath = pluginDescriptorModuleHeader.ownClasspath
    if (pluginDescriptorOwnClasspath.isEmpty()) {
      logger.error("'${pluginHeader.pluginDescriptorModuleId}' has empty own classpath, so '${pluginHeader.pluginId}' plugin won't be loaded")
      return null
    }
    val descriptor = pluginDescriptorOwnClasspath.firstNotNullOfOrNull { classpathRoot ->
      tryLoadingPluginDescriptorFromJarOrDirectory(classpathRoot, bundledPluginsDir, pluginDescriptorClasspath, pluginHeader, zipPool, loadingContext)
    }

    if (descriptor != null) {
      descriptor.ownClassPath = if (AppMode.isRunningFromDevBuild()) {
        /* when running from dev build, content modules with package prefix may be put to separate JARs under jar-cache directory, and the
           plugin loading code expects them to be in the main plugin classpath, so we need to add them explicitly */
        val modulesWithPackagePrefix = descriptor.contentModules.asSequence().filter { it.packagePrefix != null }.mapTo(HashSet()) { it.moduleId.name }
        (pluginDescriptorClasspath +
        pluginHeader.includedModules.asSequence().filter { it.loadingRule != RuntimeModuleLoadingRule.EMBEDDED && it.moduleId.name in modulesWithPackagePrefix }.flatMap {
          moduleRepository.findModuleHeader(it.moduleId)?.ownClasspath?.asSequence() ?: emptySequence()
        }).distinct()
      }
      else {
        pluginDescriptorClasspath
      }
    }
    return descriptor
  }

  private fun tryLoadingPluginDescriptorFromJarOrDirectory(
    classpathRoot: Path,
    bundledPluginsDir: Path,
    pluginDescriptorClasspath: List<Path>,
    pluginHeader: RuntimePluginHeader,
    zipFilePool: ZipEntryResolverPool,
    loadingContext: PluginDescriptorLoadingContext,
  ): PluginMainDescriptor? {
    val pathResolver = PluginXmlPathResolver(pluginJarFiles = pluginDescriptorClasspath, pool = zipFilePool)
    val pluginHeaderBasedResolver = PluginHeaderBasedXmlPathResolver(pluginHeader, moduleRepository, fallbackResolver = pathResolver)
    return if (Files.isDirectory(classpathRoot)) {
      loadDescriptorFromDir(
        dir = classpathRoot,
        loadingContext = loadingContext,
        pathResolver = pluginHeaderBasedResolver,
        pool = zipFilePool,
        isBundled = true,
        pluginDir = null,
      )
    }
    else {
      val pluginDir = determinePluginDirectory(classpathRoot, bundledPluginsDir, pluginHeader)
      loadDescriptorFromJar(
        file = classpathRoot,
        loadingContext = loadingContext,
        pathResolver = pluginHeaderBasedResolver,
        pool = zipFilePool,
        isBundled = true,
        pluginDir = pluginDir,
      )
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
      dirStream.forEach { file ->
        deferredDescriptors.add(scope.async {
          loadDescriptorFromFileOrDir(
            file = file,
            loadingContext = context,
            pool = zipFilePool,
          )
        })
      }
    }
    return scope.async { DiscoveredPluginsList(deferredDescriptors.awaitAll().filterNotNull(), PluginsSourceContext.Custom) }
  }

  /**
   * Returns the plugin directory for the plugin.
   * This is needed to ensure that code which uses [com.intellij.openapi.extensions.PluginDescriptor.getPluginPath] will work correctly.
   * Since JARs of plugin's modules may be located in different directories (until IJPL-220139 is fixed), the code tries to determine the
   * plugin directory by JARs located in standard locations (lib/ or lib/modules).
   */
  private fun determinePluginDirectory(classpathRoot: Path, bundledPluginsDir: Path, pluginHeader: RuntimePluginHeader): Path? {
    val grandparent = classpathRoot.parent.parent
    if (grandparent.parent == bundledPluginsDir) return grandparent
    return pluginHeader.includedModules
      .asSequence()
      .flatMap { included ->
        moduleRepository.findModuleHeader(included.moduleId)?.ownClasspath?.asSequence() ?: emptySequence()
      }
      .map { jarFile ->
        val parent = jarFile.parent
        if (parent.name == "modules") parent.parent.parent
        else parent.parent
      }
      .find { it.parent == bundledPluginsDir }
  }

  override fun findProductContentModuleClassesRoot(moduleId: PluginModuleId, moduleDir: Path): Path? {
    return findProductContentModuleClassesRoot(moduleId)
  }

  private fun findProductContentModuleClassesRoot(moduleId: PluginModuleId): Path? {
    var resolvedModule = moduleRepository.resolveModule(RuntimeModuleId.contentModule(moduleId.name, moduleId.namespace)).resolvedModule
    if (resolvedModule == null && moduleId.namespace == PluginModuleId.JETBRAINS_NAMESPACE) {
      /* until IJPL-241655 is implemented, we may not detect proper namespace for some modules, e.g. `intellij.cwm.connection.frontend.split`,
         so try searching with a different namespace */
      resolvedModule = moduleRepository.resolveModule(RuntimeModuleId.legacyJpsModule(moduleId.name)).resolvedModule
    }
    if (resolvedModule == null) {
      // https://youtrack.jetbrains.com/issue/CPP-38280
      // we log here, as only for JetBrainsClient it is expected that some module is not resolved
      thisLogger().debug("Skip loading product content module $moduleId because its classes root isn't present")
      return null
    }

    val paths = resolvedModule.resourceRootPaths
    val singlePath = paths.singleOrNull()
    if (singlePath == null) {
      error("Content modules are supposed to have only one resource root, but $moduleId have multiple: $paths")
    }

    return singlePath
  }
}

private const val PLATFORM_CORE_PLUGIN_DESCRIPTOR_MODULE_PROPERTY = "intellij.platform.core.plugin.descriptor.module"
private const val PLATFORM_ROOT_MODULE_PROPERTY = "intellij.platform.root.module"
private const val PLATFORM_PRODUCT_MODE_PROPERTY = "intellij.platform.product.mode"

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.util.containers.Java11Shim
import com.intellij.util.lang.ZipEntryResolverPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
@Deprecated("Used only by module-based loader")
fun deprecatedLoadCorePluginForModuleBasedLoader(
  platformPrefix: String,
  isInDevServerMode: Boolean,
  isUnitTestMode: Boolean,
  isRunningFromSources: Boolean,
  loadingContext: PluginDescriptorLoadingContext,
  pathResolver: PathResolver,
  useCoreClassLoader: Boolean,
  classLoader: ClassLoader,
  jarFileForModule: (moduleId: PluginModuleId, moduleDir: Path) -> Path?,
  pool: ZipEntryResolverPool,
): PluginMainDescriptor? {
  if (isProductWithTheOnlyDescriptor(platformPrefix) && (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    val reader = getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!
    return loadCoreProductPlugin(
      loadingContext = loadingContext,
      pathResolver = pathResolver,
      useCoreClassLoader = useCoreClassLoader,
      reader = reader,
      jarFileForModule = jarFileForModule,
      isRunningFromSourcesWithoutDevBuild = false,
      isDeprecatedLoader = true,
      pool = pool,
    )
  }
  else {
    val path = "${PluginManagerCore.META_INF}${platformPrefix}Plugin.xml"
    val reader = getResourceReader(path, classLoader) ?: return null
    return loadCoreProductPlugin(
      loadingContext = loadingContext,
      pathResolver = pathResolver,
      useCoreClassLoader = useCoreClassLoader,
      reader = reader,
      jarFileForModule = jarFileForModule,
      isRunningFromSourcesWithoutDevBuild = isRunningFromSources && !isInDevServerMode,
      isDeprecatedLoader = true,
      pool = pool,
    )
  }
}

internal fun CoroutineScope.loadPluginDescriptorsInDeprecatedUnitTestMode(
  loadingContext: PluginDescriptorLoadingContext,
  platformPrefix: String,
  isRunningFromSources: Boolean,
  mainClassLoader: ClassLoader,
  zipPool: ZipEntryResolverPool,
  customPluginDir: Path,
  bundledPluginDir: Path?,
  jarFileForModule: (PluginModuleId, Path) -> Path?,
): Deferred<List<DiscoveredPluginsList>> {
  // Do not hardcode `isRunningFromSources = true`.
  // This falsely assumes that if `isUnitTestMode = true`, then tests are running inside the monorepo.
  // For external plugins (and internal plugins developed outside the monorepo, like the Scala plugin), `isRunningFromSources` has the value `false`.
  // An incorrect value for `isRunningFromSources` leads to plugin loading issues in tests for external plugins.
  @Suppress("DEPRECATION")
  val core = deprecatedLoadCoreModules(
    loadingContext = loadingContext,
    platformPrefix = platformPrefix,
    isUnitTestMode = true,
    isInDevServerMode = false,
    isRunningFromSources = isRunningFromSources, // do not hardcode to `true`
    classLoader = mainClassLoader,
    pool = zipPool,
    jarFileForModule = jarFileForModule,
  )
  val custom = loadDescriptorsFromDir(dir = customPluginDir, loadingContext = loadingContext, isBundled = false, pool = zipPool)
  val bundled = if (bundledPluginDir == null) {
    null
  }
  else {
    loadDescriptorsFromDir(dir = bundledPluginDir, loadingContext = loadingContext, isBundled = true, pool = zipPool)
  }
  return async {
    listOfNotNull(core.await(), custom.await(), bundled?.await())
  }
}

internal fun CoroutineScope.deprecatedLoadPluginDescriptorsWithoutDistIndex(
  loadingContext: PluginDescriptorLoadingContext,
  platformPrefix: String,
  isRunningFromSources: Boolean,
  zipPool: ZipEntryResolverPool,
  mainClassLoader: ClassLoader,
  customPluginDir: Path,
  effectiveBundledPluginDir: Path,
  jarFileForModule: (PluginModuleId, Path) -> Path?,
): Deferred<List<DiscoveredPluginsList>> {
  PluginManagerCore.logger.warn("Dist index is missing or corrupted; an OLD, DEPRECATED, SOON-TO-BE-REMOVED implementation will be used")

  @Suppress("DEPRECATION")
  val core = deprecatedLoadCoreModules(
    loadingContext = loadingContext,
    platformPrefix = platformPrefix,
    isUnitTestMode = false,
    isInDevServerMode = AppMode.isRunningFromDevBuild(),
    isRunningFromSources = isRunningFromSources,
    pool = zipPool,
    classLoader = mainClassLoader,
    jarFileForModule = jarFileForModule,
  )
  val custom = loadDescriptorsFromDir(dir = customPluginDir, loadingContext = loadingContext, isBundled = false, pool = zipPool)
  val bundled = loadDescriptorsFromDir(dir = effectiveBundledPluginDir, loadingContext = loadingContext, isBundled = true, pool = zipPool)
  return async {
    listOfNotNull(core.await(), custom.await(), bundled.await())
  }
}

@Deprecated("Used only by gateway and in deprecated unit test mode (not dev-mode)")
private fun CoroutineScope.deprecatedLoadCoreModules(
  loadingContext: PluginDescriptorLoadingContext,
  platformPrefix: String,
  isUnitTestMode: Boolean,
  isInDevServerMode: Boolean,
  isRunningFromSources: Boolean,
  pool: ZipEntryResolverPool,
  classLoader: ClassLoader,
  jarFileForModule: (PluginModuleId, Path) -> Path?,
): Deferred<DiscoveredPluginsList> {
  val pathResolver = ClassPathXmlPathResolver(
    classLoader = classLoader,
    isRunningFromSourcesWithoutDevBuild = isRunningFromSources && !isInDevServerMode,
    isOptionalProductModule = { ProductLoadingStrategy.strategy.isOptionalProductModule(it) },
  )
  val useCoreClassLoader = pathResolver.isRunningFromSourcesWithoutDevBuild || platformPrefix.startsWith("CodeServer") || forceUseCoreClassloader()

  if (isProductWithTheOnlyDescriptor(platformPrefix) && (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    return async(Dispatchers.IO) {
      val reader = getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!
      val corePlugin = loadCoreProductPlugin(
        loadingContext = loadingContext,
        pathResolver = pathResolver,
        useCoreClassLoader = useCoreClassLoader,
        reader = reader,
        jarFileForModule = jarFileForModule,
        isRunningFromSourcesWithoutDevBuild = false,
        isDeprecatedLoader = true,
        pool = pool,
      )
      DiscoveredPluginsList(Java11Shim.INSTANCE.listOf(corePlugin), PluginsSourceContext.Product)
    }
  }

  val corePluginDeferred = async(Dispatchers.IO) {
    val path = "${PluginManagerCore.META_INF}${platformPrefix}Plugin.xml"
    val reader = getResourceReader(path = path, classLoader = classLoader) ?: return@async null
    loadCoreProductPlugin(
      loadingContext = loadingContext,
      pathResolver = pathResolver,
      useCoreClassLoader = useCoreClassLoader,
      reader = reader,
      jarFileForModule = jarFileForModule,
      isRunningFromSourcesWithoutDevBuild = isRunningFromSources && !isInDevServerMode,
      isDeprecatedLoader = true,
      pool = pool,
    )
  }

  val result = ArrayList<Deferred<PluginMainDescriptor?>>()
  result.add(corePluginDeferred)
  @Suppress("UrlHashCode")
  val urlToFilename = collectPluginFilesInClassPath(classLoader)
  if (urlToFilename.isNotEmpty()) {
    val libDir = if (useCoreClassLoader) null else PathManager.getLibDir()
    urlToFilename.mapTo(result) { (url, filename) ->
      async(Dispatchers.IO) {
        testOrDeprecatedLoadDescriptorFromResource(
          resource = url,
          filename = filename,
          loadingContext = loadingContext,
          pathResolver = pathResolver,
          useCoreClassLoader = useCoreClassLoader,
          pool = pool,
          libDir = libDir,
        )
      }
    }
  }
  return async {
    DiscoveredPluginsList(result.awaitAll().filterNotNull(), PluginsSourceContext.Product)
  }
}

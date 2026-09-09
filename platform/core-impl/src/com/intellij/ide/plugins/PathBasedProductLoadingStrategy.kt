// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.lang.ZipEntryResolverPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import java.nio.file.Path

internal class PathBasedProductLoadingStrategy : ProductLoadingStrategy() {
  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
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
    return scope.loadPluginDescriptorsForPathBasedLoader(
      loadingContext = loadingContext,
      isUnitTestMode = isUnitTestMode,
      isInDevServerMode = isInDevServerMode,
      isRunningFromSources = isRunningFromSources,
      mainClassLoader = mainClassLoader,
      zipPool = zipPool,
      customPluginDir = customPluginDir,
      bundledPluginDir = bundledPluginDir,
    )
  }

  override fun findProductContentModuleClassesRoot(moduleId: PluginModuleId, moduleDir: Path): Path = moduleDir.resolve("${moduleId.name}.jar")
}
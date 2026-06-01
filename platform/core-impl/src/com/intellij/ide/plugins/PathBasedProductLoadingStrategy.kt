// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.ZipEntryResolverPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

internal class PathBasedProductLoadingStrategy : ProductLoadingStrategy() {
  // this property returns hardcoded Strings instead of ProductMode, because currently ProductMode class isn't available in dependencies of this module
  override val currentModeId: String
    get() = when {
      AppMode.isRemoteDevHost() -> "backend"
      PlatformUtils.isJetBrainsClient() -> "frontend" //this should be removed after all tests starts using the module-based loader to run the frontend process 
      else -> "monolith"
    }

  // this strategy doesn't support advancing between modes, so the flow always reports the current mode id
  override val currentModeIdFlow: StateFlow<String> = MutableStateFlow(currentModeId)

  override fun advanceToLightWithRdConnectionMode(): Boolean {
    throw NotImplementedError("It seems that you are starting ij-light with wrong ProductLoadingStrategy (ModuleBasedProductLoadingStrategy is expected)")
  }

  override fun advanceToFrontendMode(): Boolean {
    throw NotImplementedError("It seems that you are starting ij-light with wrong ProductLoadingStrategy (ModuleBasedProductLoadingStrategy is expected)")
  }

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
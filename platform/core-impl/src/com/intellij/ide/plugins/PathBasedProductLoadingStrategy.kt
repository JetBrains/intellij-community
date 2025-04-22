// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.ZipEntryResolverPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import java.nio.file.Path

internal class PathBasedProductLoadingStrategy : ProductLoadingStrategy() {
  // this property returns hardcoded Strings instead of ProductMode, because currently ProductMode class isn't available in dependencies of this module
  override val currentModeId: String
    get() = when {
      AppMode.isRemoteDevHost() -> "backend"
      PlatformUtils.isJetBrainsClient() -> "frontend" //this should be removed after all tests starts using the module-based loader to run the frontend process 
      else -> "monolith"
    }

  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
  }

  override fun loadPluginDescriptors(
    scope: CoroutineScope,
    loadingContext: PluginDescriptorLoadingContext,
    customPluginDir: Path,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    isRunningFromSources: Boolean,
    zipPool: ZipEntryResolverPool,
    mainClassLoader: ClassLoader,
  ): List<Deferred<IdeaPluginDescriptorImpl?>> {
    return scope.loadPluginDescriptorsImpl(
      loadingContext = loadingContext,
      isUnitTestMode = isUnitTestMode,
      isRunningFromSources = isRunningFromSources,
      mainClassLoader = mainClassLoader,
      zipPool = zipPool,
      customPluginDir = customPluginDir,
      bundledPluginDir = bundledPluginDir,
    )
  }

  override fun isOptionalProductModule(moduleName: String): Boolean = false

  override fun findProductContentModuleClassesRoot(moduleName: String, moduleDir: Path): Path = moduleDir.resolve("$moduleName.jar")
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import java.nio.file.Path

internal class PathBasedProductLoadingStrategy : ProductLoadingStrategy() {
  // this property returns hardcoded Strings instead of ProductMode, because currently ProductMode class isn't available in dependencies of this module
  override val currentModeId: String
    get() = if (AppMode.isRemoteDevHost()) "backend" else "monolith"

  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
  }

  override fun loadPluginDescriptors(
    scope: CoroutineScope,
    context: DescriptorListLoadingContext,
    customPluginDir: Path,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    isRunningFromSources: Boolean,
    zipFilePool: ZipFilePool,
    mainClassLoader: ClassLoader,
  ): List<Deferred<IdeaPluginDescriptorImpl?>> {
    return scope.loadPluginDescriptorsImpl(
      context = context,
      isUnitTestMode = isUnitTestMode,
      isRunningFromSources = isRunningFromSources,
      mainClassLoader = mainClassLoader,
      zipFilePool = zipFilePool,
      customPluginDir = customPluginDir,
      bundledPluginDir = bundledPluginDir,
    )
  }

  override fun isOptionalProductModule(moduleName: String): Boolean = false

  override fun findProductContentModuleClassesRoot(moduleName: String, moduleDir: Path): Path = moduleDir.resolve("$moduleName.jar")
}
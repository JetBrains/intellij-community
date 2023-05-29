// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.*
import com.intellij.platform.runtime.repository.ProductModules
import com.intellij.platform.runtime.repository.RuntimeModuleGroup
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.nio.file.Files
import java.nio.file.Path

class ModuleBasedPluginDescriptorLoadingStrategy(private val productModules: ProductModules) : PluginDescriptorLoadingStrategy() {
  override fun loadBundledPluginDescriptors(scope: CoroutineScope,
                                            bundledPluginDir: Path?,
                                            isUnitTestMode: Boolean,
                                            context: DescriptorListLoadingContext,
                                            zipFilePool: ZipFilePool?): List<Deferred<IdeaPluginDescriptorImpl?>> {
    return productModules.bundledPluginModuleGroups.map { moduleGroup ->
      scope.async {
        loadPluginDescriptorFromRuntimeModule(moduleGroup, context, zipFilePool)
      }
    }
  }
  
  private fun loadPluginDescriptorFromRuntimeModule(pluginModuleGroup: RuntimeModuleGroup,
                                                    context: DescriptorListLoadingContext,
                                                    zipFilePool: ZipFilePool?): IdeaPluginDescriptorImpl? {
    val resourceRoot = pluginModuleGroup.includedModules.singleOrNull()?.moduleDescriptor?.resourceRootPaths?.singleOrNull()
                       ?: error("Only single-module plugins are supported for now, so '${pluginModuleGroup}' cannot be loaded")
    return if (Files.isDirectory(resourceRoot)) {
      loadDescriptorFromDir(
        file = resourceRoot,
        descriptorRelativePath = PluginManagerCore.PLUGIN_XML_PATH,
        pluginPath = resourceRoot,
        context = context,
        isBundled = true,
        isEssential = false,
        useCoreClassLoader = false,
        pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
      )
    }
    else {
      loadDescriptorFromJar(
        file = resourceRoot,
        fileName = PluginManagerCore.PLUGIN_XML,
        pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
        parentContext = context,
        isBundled = true,
        isEssential = false,
        useCoreClassLoader = false,
        pluginPath = resourceRoot.parent.parent,
        pool = zipFilePool
      ).also {
        it?.jarFiles = listOf(resourceRoot)
      }
    }
  }
}

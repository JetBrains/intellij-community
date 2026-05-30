// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.DataLoader
import com.intellij.ide.plugins.PathResolver
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.createXIncludeLoader
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.pluginSystem.parser.impl.parsePluginXml
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import java.nio.file.Path

/**
 * Implementation of [PathResolver] that uses data from [com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl] and [com.intellij.platform.runtime.repository.RuntimeModuleRepository]
 * to determine paths to plugin files.
 */
internal class PluginHeaderBasedXmlPathResolver(
  private val header: RuntimePluginHeader,
  private val moduleRepository: RuntimeModuleRepository,
  private val fallbackResolver: PathResolver,
) : PathResolver by fallbackResolver {

  override fun resolveModuleFile(
    readContext: PluginDescriptorReaderContext,
    dataLoader: DataLoader,
    path: String,
  ): PluginDescriptorBuilder {
    val moduleName = path.removeSuffix(".xml")
    val includedModule = header.includedModules.find { it.moduleId.name == moduleName }
    if (includedModule != null) {
      val moduleHeader = moduleRepository.findModuleHeader(includedModule.moduleId)
      if (moduleHeader != null) {
        val input = moduleHeader.readFile(path) ?: error("Cannot resolve $path in $moduleHeader")
        return parsePluginXml(input, path, readContext, createXIncludeLoader(this, dataLoader))
      }
    }
    return fallbackResolver.resolveModuleFile(readContext, dataLoader, path)
  }

  override fun resolveCustomModuleClassesRoots(moduleId: PluginModuleId): List<Path> {
    val runtimeModuleId = RuntimeModuleId.contentModule(moduleId.name, moduleId.namespace)
    val legacyModuleId = RuntimeModuleId.legacyJpsModule(moduleId.name)
    val moduleHeader = moduleRepository.findModuleHeader(runtimeModuleId) ?: moduleRepository.findModuleHeader(legacyModuleId)
    return moduleHeader?.ownClasspath ?: emptyList()
  }
}

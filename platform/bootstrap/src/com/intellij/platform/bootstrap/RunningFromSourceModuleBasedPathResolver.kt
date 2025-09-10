// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.DataLoader
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.PathResolver
import com.intellij.ide.plugins.toXIncludeLoader
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import java.nio.file.Path

/**
 * This class is used to resolve content modules in the core plugin when running from sources with the module-based loader.
 */
internal class RunningFromSourceModuleBasedPathResolver(
  private val moduleRepository: RuntimeModuleRepository,
  private val fallbackResolver: PathResolver,
) : PathResolver by fallbackResolver {
  override fun resolveModuleFile(readContext: PluginDescriptorReaderContext, dataLoader: DataLoader, path: String): PluginDescriptorBuilder {
    val moduleName = path.removeSuffix(".xml")
    val moduleDescriptor = moduleRepository.resolveModule(RuntimeModuleId.module(moduleName)).resolvedModule
    if (moduleDescriptor != null) {
      val input = moduleDescriptor.readFile(path) ?: error("Cannot resolve $path in $moduleDescriptor")
      val reader = PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader))
      reader.consume(input, path)
      return reader.getBuilder()
    }
    return fallbackResolver.resolveModuleFile(readContext = readContext, dataLoader = dataLoader, path = path)
  }

  override fun resolveCustomModuleClassesRoots(moduleId: PluginModuleId): List<Path> {
    val moduleDescriptor = moduleRepository.resolveModule(RuntimeModuleId.raw(moduleId.id)).resolvedModule
    if (moduleDescriptor?.moduleId?.stringId?.contains(".charts") == true) {
    }
    return moduleDescriptor?.resourceRootPaths ?: emptyList()
  }
}

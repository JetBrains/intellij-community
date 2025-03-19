// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.DataLoader
import com.intellij.ide.plugins.PathResolver
import com.intellij.ide.plugins.PluginXmlPathResolver
import com.intellij.ide.plugins.toXIncludeLoader
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.runtime.product.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import java.nio.file.Path

/**
 * Implementation of [PathResolver] which can load module descriptors not only from the main plugin JAR file, unlike [PluginXmlPathResolver]
 * which always loads them from the JAR file containing plugin.xml file.
 */
internal class ModuleBasedPluginXmlPathResolver(
  private val includedModules: List<IncludedRuntimeModule>,
  private val optionalModuleIds: Set<RuntimeModuleId>,
  private val fallbackResolver: PathResolver,
) : PathResolver {

  override fun resolveModuleFile(
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    path: String,
  ): PluginDescriptorBuilder {
    // if there are multiple JARs,
    // it may happen that module descriptor is located in other JARs (e.g., in case of 'com.intellij.java.frontend' plugin),
    // so try loading it from the root of the corresponding module
    val moduleName = path.removeSuffix(".xml")
    val moduleDescriptor = includedModules.find { it.moduleDescriptor.moduleId.stringId == moduleName }?.moduleDescriptor
    if (moduleDescriptor != null) {
      val input = moduleDescriptor.readFile(path) ?: error("Cannot resolve $path in $moduleDescriptor")
      val reader = PluginDescriptorFromXmlStreamConsumer(readContext, toXIncludeLoader(dataLoader))
      reader.consume(input, path)
      return reader.getBuilder()
    }
    else if (RuntimeModuleId.module(moduleName) in optionalModuleIds) {
      return PluginDescriptorBuilder.builder().apply { `package` = "unresolved.$moduleName" }
    }
    return fallbackResolver.resolveModuleFile(readContext = readContext, dataLoader = dataLoader, path = path)
  }

  override fun resolveCustomModuleClassesRoots(moduleName: String): List<Path> {
    val moduleDescriptor = includedModules.find { it.moduleDescriptor.moduleId.stringId == moduleName }?.moduleDescriptor
    return moduleDescriptor?.resourceRootPaths ?: emptyList()
  }

  override fun loadXIncludeReference(
    dataLoader: DataLoader,
    path: String,
  ): XIncludeLoader.LoadedXIncludeReference? {
    return fallbackResolver.loadXIncludeReference(
      dataLoader = dataLoader,
      path = path,
    )
  }

  override fun resolvePath(
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    relativePath: String,
  ): PluginDescriptorBuilder? {
    return fallbackResolver.resolvePath(
      readContext = readContext,
      dataLoader = dataLoader,
      relativePath = relativePath,
    )
  }
}

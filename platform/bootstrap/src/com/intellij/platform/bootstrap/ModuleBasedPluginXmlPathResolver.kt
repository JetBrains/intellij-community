// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.*
import com.intellij.platform.plugins.parser.impl.*
import com.intellij.platform.plugins.parser.impl.elements.DependenciesElement
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
  private val notLoadedModuleIds: Map<RuntimeModuleId, List<RuntimeModuleId>>,
  private val fallbackResolver: PathResolver,
) : PathResolver {

  override fun resolveModuleFile(
    readContext: PluginDescriptorReaderContext,
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
    else {
      val moduleId = RuntimeModuleId.module(moduleName)
      if (moduleId in optionalModuleIds) {
        // TODO here we should restore the actual content module "header" with dependency information
        return PluginDescriptorBuilder.builder().apply {
          `package` = "unresolved.$moduleName"

          val reasonsWhyNotLoaded = notLoadedModuleIds[moduleId] ?: emptyList()
          if (reasonsWhyNotLoaded.isNotEmpty()) {
            for (reason in reasonsWhyNotLoaded) {
              addDependency(DependenciesElement.ModuleDependency(reason.stringId))
            }
          }
          else {
            addDependency(DependenciesElement.ModuleDependency("incompatible.with.product.mode.or.unresolved"))
          }
        }
      }
    }
    return fallbackResolver.resolveModuleFile(readContext = readContext, dataLoader = dataLoader, path = path)
  }

  override fun resolveCustomModuleClassesRoots(moduleId: PluginModuleId): List<Path> {
    val moduleDescriptor = includedModules.find { it.moduleDescriptor.moduleId.stringId == moduleId.id }?.moduleDescriptor
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
    readContext: PluginDescriptorReaderContext,
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

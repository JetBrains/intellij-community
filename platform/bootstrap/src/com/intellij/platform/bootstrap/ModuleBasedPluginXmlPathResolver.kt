// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.*
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
    readInto: RawPluginDescriptor?,
  ): RawPluginDescriptor {
    // if there are multiple JARs,
    // it may happen that module descriptor is located in other JARs (e.g., in case of 'com.intellij.java.frontend' plugin),
    // so try loading it from the root of the corresponding module
    val moduleName = path.removeSuffix(".xml")
    val moduleDescriptor = includedModules.find { it.moduleDescriptor.moduleId.stringId == moduleName }?.moduleDescriptor
    if (moduleDescriptor != null) {
      val input = moduleDescriptor.readFile(path) ?: error("Cannot resolve $path in $moduleDescriptor")
      return readModuleDescriptor(
        input = input,
        readContext = readContext,
        pathResolver = this,
        dataLoader = dataLoader,
        includeBase = null,
        readInto = readInto,
        locationSource = path,
      )
    }
    else if (RuntimeModuleId.module(moduleName) in optionalModuleIds) {
      return RawPluginDescriptor().apply { `package` = "unresolved.$moduleName" }
    }
    return fallbackResolver.resolveModuleFile(readContext = readContext, dataLoader = dataLoader, path = path, readInto = readInto)
  }

  override fun resolveCustomModuleClassesRoots(moduleName: String): List<Path> {
    val moduleDescriptor = includedModules.find { it.moduleDescriptor.moduleId.stringId == moduleName }?.moduleDescriptor
    return moduleDescriptor?.resourceRootPaths ?: emptyList()
  }

  override fun loadXIncludeReference(
    readInto: RawPluginDescriptor,
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    base: String?,
    relativePath: String,
  ): Boolean {
    return fallbackResolver.loadXIncludeReference(
      readInto = readInto,
      readContext = readContext,
      dataLoader = dataLoader,
      base = base,
      relativePath = relativePath,
    )
  }

  override fun resolvePath(
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    relativePath: String,
    readInto: RawPluginDescriptor?,
  ): RawPluginDescriptor? {
    return fallbackResolver.resolvePath(
      readContext = readContext,
      dataLoader = dataLoader,
      relativePath = relativePath,
      readInto = readInto,
    )
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.*
import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import java.nio.file.Path

/**
 * Implementation of [PathResolver] which can load module descriptors not only from the main plugin JAR file, unlike [PluginXmlPathResolver]
 * which always loads them from the JAR file containing plugin.xml file.
 */
class ModuleBasedPluginXmlPathResolver(private val allResourceRoots: List<Path>,
                                       private val includedModules: List<IncludedRuntimeModule>) : PathResolver {
  private val fallbackResolver = PluginXmlPathResolver(allResourceRoots)

  override fun resolveModuleFile(readContext: ReadModuleContext,
                                 dataLoader: DataLoader,
                                 path: String,
                                 readInto: RawPluginDescriptor?): RawPluginDescriptor {
    if (allResourceRoots.size > 1) {
      /* if there are multiple JARs it may happen that module descriptor is located in other JARs (e.g. in case of 'com.intellij.java.frontend' 
         plugin), so try loading it from the root of the corresponding module */
      val moduleName = path.removeSuffix(".xml")
      val moduleDescriptor = includedModules.find { it.moduleDescriptor.moduleId.stringId == moduleName }?.moduleDescriptor
      if (moduleDescriptor != null) {
        val input = moduleDescriptor.readFile(path)
                    ?: error("Cannot resolve $path in $moduleDescriptor")
        return readModuleDescriptor(input, readContext, pathResolver = this, dataLoader, includeBase = null, readInto, locationSource = null)
      }
    }
    return fallbackResolver.resolveModuleFile(readContext, dataLoader, path, readInto)
  }

  override fun loadXIncludeReference(readInto: RawPluginDescriptor,
                                     readContext: ReadModuleContext,
                                     dataLoader: DataLoader,
                                     base: String?,
                                     relativePath: String): Boolean {
    return fallbackResolver.loadXIncludeReference(readInto, readContext, dataLoader, base, relativePath)
  }

  override fun resolvePath(readContext: ReadModuleContext,
                           dataLoader: DataLoader,
                           relativePath: String,
                           readInto: RawPluginDescriptor?): RawPluginDescriptor? {
    return fallbackResolver.resolvePath(readContext, dataLoader, relativePath, readInto)
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import com.intellij.platform.plugins.parser.impl.XIncludeLoader.LoadedXIncludeReference
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface PathResolver {
  val isFlat: Boolean
    get() = false

  /**
   * @param path absolute path from a resource root, without leading '/' (e.g., `META-INF/extensions.xml`)
   */
  fun loadXIncludeReference(dataLoader: DataLoader, path: String): LoadedXIncludeReference?

  fun resolvePath(readContext: PluginDescriptorReaderContext, dataLoader: DataLoader, relativePath: String): PluginDescriptorBuilder?

  // module in a new file name format must always be resolved
  fun resolveModuleFile(readContext: PluginDescriptorReaderContext, dataLoader: DataLoader, path: String): PluginDescriptorBuilder

  /**
   * Returns custom classes roots for a content module [moduleId] if any.
   * If the module is located in the standard place (lib/modules/module.name.jar) or merged with one of JARs loaded by the main classloader, an empty list is returned. 
   */
  fun resolveCustomModuleClassesRoots(moduleId: PluginModuleId): List<Path> {
    return emptyList()
  }
}

@ApiStatus.Internal
fun PathResolver.toXIncludeLoader(dataLoader: DataLoader): XIncludeLoader = object : XIncludeLoader {
  override fun loadXIncludeReference(path: String): LoadedXIncludeReference? {
    return loadXIncludeReference(dataLoader, path)
  }

  override fun toString(): String = dataLoader.toString()
}



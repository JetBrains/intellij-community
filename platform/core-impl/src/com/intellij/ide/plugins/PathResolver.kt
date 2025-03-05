// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.parser.RawPluginDescriptor
import com.intellij.ide.plugins.parser.ReadModuleContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface PathResolver: XIncludeLoader {
  val isFlat: Boolean
    get() = false

  fun resolvePath(readContext: ReadModuleContext, dataLoader: DataLoader, relativePath: String): RawPluginDescriptor?

  // module in a new file name format must always be resolved
  fun resolveModuleFile(readContext: ReadModuleContext, dataLoader: DataLoader, path: String): RawPluginDescriptor

  /**
   * Returns custom classes roots for a content module [moduleName] if any. 
   * If the module is located in the standard place (lib/modules/module.name.jar) or merged with one of JARs loaded by the main classloader, an empty list is returned. 
   */
  fun resolveCustomModuleClassesRoots(moduleName: String): List<Path> {
    return emptyList()
  }
}


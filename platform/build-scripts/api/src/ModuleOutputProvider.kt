// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

interface ModuleOutputProvider {
  fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean = false): ByteArray?

  fun findModule(name: String): JpsModule?

  fun findRequiredModule(name: String): JpsModule

  fun getModuleOutputRoots(module: JpsModule, forTests: Boolean = false): List<Path>
}
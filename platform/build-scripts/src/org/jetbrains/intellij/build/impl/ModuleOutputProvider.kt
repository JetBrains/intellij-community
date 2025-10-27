// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

interface ModuleOutputProvider {
  companion object {
    fun jps(modules: List<JpsModule>): ModuleOutputProvider {
      return object : ModuleOutputProvider {
        private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }

        override fun findModule(name: String): JpsModule? = nameToModule.get(name.removeSuffix("._test"))

        override fun findRequiredModule(name: String): JpsModule {
          return checkNotNull(findModule(name)) {
            "Cannot find required module '$name' in the project"
          }
        }

        override suspend fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
          val url = JpsJavaExtensionService.getInstance().getOutputUrl(/* module = */ module, /* forTests = */ forTests)
          requireNotNull(url) {
            "Output directory for ${module.name} isn't set"
          }
          return listOf(Path.of(JpsPathUtil.urlToPath(url)))
        }
      }
    }
  }

  fun findModule(name: String): JpsModule?

  fun findRequiredModule(name: String): JpsModule

  suspend fun getModuleOutputRoots(module: JpsModule, forTests: Boolean = false): List<Path>
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

internal class JpsModuleOutputProvider(modules: List<JpsModule>) : ModuleOutputProvider {
  private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val outputRoots = getModuleOutputRoots(module, forTests)
    val outputDir = outputRoots.singleOrNull() ?: error("More than one output root for module '${module.name}': ${outputRoots.joinToString()}")
    val file = outputDir.resolve(relativePath)
    try {
      return Files.readAllBytes(file)
    }
    catch (_: NoSuchFileException) {
      return null
    }
  }

  override fun findModule(name: String): JpsModule? = nameToModule.get(name.removeSuffix("._test"))

  override fun findRequiredModule(name: String): JpsModule {
    return requireNotNull(findModule(name)) {
      "Cannot find required module '$name' in the project"
    }
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val url = JpsJavaExtensionService.getInstance().getOutputUrl(/* module = */ module, /* forTests = */ forTests)
    requireNotNull(url) {
      "Output directory for ${module.name} isn't set"
    }
    return listOf(Path.of(JpsPathUtil.urlToPath(url)))
  }
}

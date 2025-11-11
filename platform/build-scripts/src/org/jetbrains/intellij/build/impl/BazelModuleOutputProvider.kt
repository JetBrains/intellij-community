// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.io.toByteArray
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

internal class BazelModuleOutputProvider(modules: List<JpsModule>, projectHome: Path) : ModuleOutputProvider {
  private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }

  private val modulesToOutputRoots: Map<String, BazelCompilationContext.BazelTargetsInfo.ModuleOutputRoots> by lazy {
    BazelCompilationContext.BazelTargetsInfo.loadModulesOutputRootsFromBazelTargetsJson(projectHome)
  }

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val result = getModuleOutputRoots(module, forTests).mapNotNull { moduleOutput ->
      var fileContent: ByteArray? = null
      readZipFile(moduleOutput) { name, data ->
        if (name == relativePath) {
          fileContent = data().toByteArray()
          ZipEntryProcessorResult.STOP
        }
        else {
          ZipEntryProcessorResult.CONTINUE
        }
      }
      return@mapNotNull fileContent
    }
    check(result.size < 2) {
      "More than one '$relativePath' file for module '${module.name}' in output roots"
    }
    return result.singleOrNull()
  }

  override fun findModule(name: String): JpsModule? = nameToModule.get(name.removeSuffix("._test"))

  override fun findRequiredModule(name: String): JpsModule {
    return requireNotNull(findModule(name)) {
      "Cannot find required module '$name' in the project"
    }
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val moduleOutputRoots = requireNotNull(modulesToOutputRoots.get(module.name)) { "No output roots for module '${module.name}'" }
    return if (forTests) moduleOutputRoots.testJars else moduleOutputRoots.productionJars
  }
}
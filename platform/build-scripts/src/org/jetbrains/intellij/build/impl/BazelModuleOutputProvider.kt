// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.io.toByteArray
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal class BazelModuleOutputProvider(modules: List<JpsModule>, val projectHome: Path, val bazelOutputRoot: Path) : ModuleOutputProvider {
  private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }

  private val bazelTargetsMap: BazelCompilationContext.BazelTargetsInfo.TargetsFile by lazy {
    BazelCompilationContext.BazelTargetsInfo.loadBazelTargetsJson(projectHome)
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

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    val librariesTable = if (moduleLibraryModuleName == null) {
      bazelTargetsMap.projectLibraries
    }
    else {
      val module = bazelTargetsMap.modules[moduleLibraryModuleName] ?: error("Cannot find module '$moduleLibraryModuleName' in the project")
      module.moduleLibraries
    }

    val libraryMoniker = "library '$libraryName' " +
                         if (moduleLibraryModuleName == null) "(project level)" else "(in module '$moduleLibraryModuleName'"
    val library = librariesTable[libraryName] ?: error(
      "Cannot find $libraryMoniker"
    )

    val paths = library.jars.map { bazelOutputRoot.resolve(it) }

    check(paths.isNotEmpty()) {
      "No files found for $libraryMoniker"
    }

    for (path in paths) {
      check(path.isRegularFile()) {
        "Library file '$path' does not exists, required for $libraryMoniker. Locally please run ./bazel-build-all.cmd"
      }
    }

    return paths
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val moduleDescription = bazelTargetsMap.modules[module.name] ?: error("Cannot find module '${module.name}' in the project")
    val jarsRelative = if (forTests) moduleDescription.testJars else moduleDescription.productionJars
    val jars = jarsRelative.map { projectHome.resolve(it) }
    for (path in jars) {
      check(path.isRegularFile()) {
        "Module output '$path' does not exists, required for module ${module.name}. Locally please run ./bazel-build-all.cmd"
      }
    }
    return jars
  }
}
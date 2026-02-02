// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal class JpsModuleOutputProvider(private val project: JpsProject, override val useTestCompilationOutput: Boolean) : ModuleOutputProvider {
  private val modules = project.modules
  private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val outputDir = requireNotNull(JpsJavaExtensionService.getInstance().getOutputDirectoryPath(/* module = */ module, /* forTests = */ forTests)) {
      "Output directory for ${module.name} isn't set"
    }
    val file = outputDir.resolve(relativePath)
    try {
      return Files.readAllBytes(file)
    }
    catch (_: NoSuchFileException) {
      return null
    }
  }

  override suspend fun readFileContentFromModuleOutputAsync(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    return readFileContentFromModuleOutput(module, relativePath, forTests)
  }

  override fun findModule(name: String): JpsModule? = nameToModule.get(name.removeSuffix("._test"))

  override fun findRequiredModule(name: String): JpsModule {
    return requireNotNull(findModule(name)) {
      "Cannot find required module '$name' in the project"
    }
  }

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    val module = moduleLibraryModuleName?.let { findRequiredModule(it) }
    val library = if (module != null) {
      module.libraryCollection.findLibrary(libraryName) ?: error("Could not find module-level library $libraryName in module ${module.name}")
    }
    else {
      project.libraryCollection.findLibrary(libraryName) ?: error("Could not find project-level library $libraryName")
    }

    val libraryMoniker = "library '$libraryName' " + if (moduleLibraryModuleName == null) "(project level)" else "(in module '$moduleLibraryModuleName'"

    val paths = library.getPaths(JpsOrderRootType.COMPILED)
    for (path in paths) {
      check(path.isRegularFile()) {
        "Library file '$path' does not exists, required for $libraryMoniker"
      }
    }

    return paths
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val file = requireNotNull(JpsJavaExtensionService.getInstance().getOutputDirectoryPath(/* module = */ module, /* forTests = */ forTests)) {
      "Output directory for ${module.name} isn't set"
    }
    return listOf(file)
  }

  override suspend fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
    return findFileInAnyModuleOutput(
      modules = modules,
      relativePath = relativePath,
      provider = this,
      moduleNamePrefix = moduleNamePrefix,
      processedModules = processedModules,
    )
  }

  override fun getModuleImlFile(module: JpsModule): Path {
    val baseDir = requireNotNull(JpsModelSerializationDataService.getBaseDirectoryPath(module)) {
      "Cannot find base directory for module ${module.name}"
    }
    return baseDir.resolve("${module.name}.iml")
  }
}

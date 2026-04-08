// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@Internal
class JpsModuleOutputProviderState(
  @JvmField val project: JpsProject,
) {
  private val index = ModuleOutputProviderIndex(project.modules)

  val modules: List<JpsModule>
    get() = index.modules

  fun findModule(name: String): JpsModule? = index.findModule(name)

  fun findRequiredModule(name: String): JpsModule = index.findRequiredModule(name)

  fun getProjectLibraryToModuleMap(): Map<String, String> = index.getProjectLibraryToModuleMap()

  fun getModuleImlFile(module: JpsModule): Path = index.getModuleImlFile(module)

  fun createProvider(useTestCompilationOutput: Boolean): ModuleOutputProvider {
    return JpsModuleOutputProvider(state = this, useTestCompilationOutput = useTestCompilationOutput)
  }
}

internal class JpsModuleOutputProvider(
  private val state: JpsModuleOutputProviderState,
  override val useTestCompilationOutput: Boolean,
) : ModuleOutputProvider {
  constructor(project: JpsProject, useTestCompilationOutput: Boolean) : this(
    state = JpsModuleOutputProviderState(project),
    useTestCompilationOutput = useTestCompilationOutput,
  )

  override fun getAllModules(): List<JpsModule> = state.modules

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val outputDir = requireNotNull(JpsJavaExtensionService.getInstance().getOutputDirectoryPath(/* module = */ module, /* forTests = */ forTests)) {
      "Output directory for ${module.name} isn't set"
    }
    val file = outputDir.resolve(relativePath)
    try {
      return withContext(Dispatchers.IO) { Files.readAllBytes(file) }
    }
    catch (_: NoSuchFileException) {
      return null
    }
  }

  override fun findModule(name: String): JpsModule? = state.findModule(name)

  override fun findRequiredModule(name: String): JpsModule = state.findRequiredModule(name)

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    val project = state.project
    val module = moduleLibraryModuleName?.let { findRequiredModule(it) }
    val library = if (module == null) {
      project.libraryCollection.findLibrary(libraryName) ?: error("Could not find project-level library $libraryName")
    }
    else {
      module.libraryCollection.findLibrary(libraryName) ?: error("Could not find module-level library $libraryName in module ${module.name}")
    }

    val libraryMoniker = "library '$libraryName' " + if (moduleLibraryModuleName == null) "(project level)" else "(in module '$moduleLibraryModuleName'"

    val paths = library.getPaths(JpsOrderRootType.COMPILED)
    for (path in paths) {
      check(Files.isRegularFile(path)) {
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
      modules = state.modules,
      relativePath = relativePath,
      provider = this,
      moduleNamePrefix = moduleNamePrefix,
      processedModules = processedModules,
    )
  }

  override fun getProjectLibraryToModuleMap(): Map<String, String> = state.getProjectLibraryToModuleMap()

  override fun getModuleImlFile(module: JpsModule): Path = state.getModuleImlFile(module)
}

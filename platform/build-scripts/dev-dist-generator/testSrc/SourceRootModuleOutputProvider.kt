// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.JpsModuleOutputProviderState
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path

/**
 * A [ModuleOutputProvider] over the source roots of [project], for a test that reads the model and builds nothing.
 *
 * The source derivations - `derivePluginJars`, `deriveDevDistPlatformJars` and the plugin model validator - read the
 * project model, a library root and a descriptor out of a resource root. None of them reads a compiled class, so a
 * test needs no compilation output to run them. The two operations that answer only for a build throw.
 */
class SourceRootModuleOutputProvider(private val project: JpsProject) : ModuleOutputProvider {
  private val libraryProvider = JpsModuleOutputProviderState(project).createProvider(useTestCompilationOutput = false)

  override val useTestCompilationOutput: Boolean
    get() = false

  override fun getAllModules(): List<JpsModule> = project.modules

  override fun findModule(name: String): JpsModule? = project.findModuleByName(name)

  override fun findRequiredModule(name: String): JpsModule = findModule(name) ?: error("Cannot find module '$name'")

  override fun findFileInModuleSources(module: JpsModule, relativePath: String, onlyProductionSources: Boolean): Path? {
    return libraryProvider.findFileInModuleSources(module = module, relativePath = relativePath, onlyProductionSources = onlyProductionSources)
  }

  override fun findModulesWithSourceFile(relativePath: String): List<JpsModule> = libraryProvider.findModulesWithSourceFile(relativePath)

  override fun getModuleImlFile(module: JpsModule): Path = throw UnsupportedOperationException("This provider reads source roots only")

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    return libraryProvider.findLibraryRoots(libraryName, moduleLibraryModuleName)
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    throw UnsupportedOperationException("This provider reads source roots only")
  }

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val file = module.sourceRoots
      .asSequence()
      .filter { it.rootType.isForTests == forTests }
      .firstNotNullOfOrNull { JpsJavaExtensionService.getInstance().findSourceFile(it, relativePath) }
    return file?.let { Files.readAllBytes(it) }
  }
}

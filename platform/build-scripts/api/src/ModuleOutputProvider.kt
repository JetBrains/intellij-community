// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

interface ModuleOutputProvider {
  val useTestCompilationOutput: Boolean

  /**
   * Returns all modules from the project model if available.
   * Used for graph enrichment in analysis-only flows.
   */
  fun getAllModules(): List<JpsModule> = emptyList()

  fun findModule(name: String): JpsModule?

  /**
   * Returns the path to the module's .iml file.
   */
  fun getModuleImlFile(module: JpsModule): Path

  fun findRequiredModule(name: String): JpsModule

  fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String? = null): List<Path>

  /**
   * Returns a map from project library name to library module name.
   *
   * This is required to translate project-level JPS library dependencies
   * (e.g., assertJ, JUnit5Params) into intellij.libraries.* modules when
   * building the plugin graph and DSL test plugin content.
   *
   * Will be removed as soon as all indirect usages are replaced.
   */
  fun getProjectLibraryToModuleMap(): Map<String, String>

  fun getModuleOutputRoots(module: JpsModule, forTests: Boolean = false): List<Path>

  /**
   * Searches for a file across module outputs.
   * Used for xi:include resolution where the included file may be in any module, not just dependencies.
   * Returns the file content if found, or null if the file doesn't exist in any module output.
   *
   * @param moduleNamePrefix if specified, only searches in modules whose name starts with this prefix
   * @param processedModules if specified, skips modules that are already in this set (and adds searched modules to it)
   */
  suspend fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String? = null, processedModules: MutableSet<String>? = null): ByteArray? = null

  @Experimental
  suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean = false): ByteArray?
}

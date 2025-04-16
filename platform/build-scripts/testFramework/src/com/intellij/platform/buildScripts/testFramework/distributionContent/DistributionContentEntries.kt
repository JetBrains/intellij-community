// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
  /**
   * The file name in distribution.
   */
  @JvmField val name: String,

  @JvmField val os: String? = null,
  @JvmField val arch: String? = null,

  /**
   * The list of included in the file project libraries.
   */
  @JvmField val projectLibraries: List<ProjectLibraryEntry> = emptyList(),

  /**
   * The list of included in the file module outputs.
   */
  @JvmField val modules: List<ModuleEntry> = emptyList(),
  @JvmField val contentModules: List<ModuleEntry> = emptyList(),

  @JvmField val library: String? = null,
  @JvmField val module: String? = null,
  @JvmField val files: List<ModuleLibraryFile> = emptyList(),
  @JvmField val reason: String? = null,

  @JvmField val bundled: List<String> = emptyList(),
  @JvmField val nonBundled: List<String> = emptyList(),
) {
  fun compareImportantFields(o: FileEntry): Boolean {
    if (name == o.name && projectLibraries.size == o.projectLibraries.size && modules == o.modules) {
      return projectLibraries.asSequence().zip(o.projectLibraries.asSequence()).all {
        it.first.compareImportantFields(it.second)
      }
    }
    return false
  }
}

@Serializable
data class ProjectLibraryEntry(
  /**
   * The library name.
   */
  @JvmField val name: String,

  /**
   * The list of library files.
   */
  @JvmField val files: List<ProjectLibraryFile> = emptyList(),

  /**
   * The modules that use the library.
   */
  @JvmField val dependentModules: Map<String, List<String>> = emptyMap(),

  /**
   * The reason why the project library was included in the distribution file.
   */
  @JvmField val reason: String? = null,
) {
  fun compareImportantFields(o: ProjectLibraryEntry) = name == o.name && files == o.files && reason == o.reason
}

@Serializable
data class ModuleEntry(
  /**
   * The library name.
   */
  @JvmField val name: String,

  /**
   * The module output size.
   */
  @JvmField val size: Int = 0,

  @JvmField val reason: String? = null,

  /**
   * The list of included module libraries.
   */
  @JvmField val libraries: Map<String, List<ModuleLibraryFile>> = emptyMap(),
)

@Serializable
data class ProjectLibraryFile(
  /**
   * The file name.
   */
  @JvmField val name: String,

  /**
   * The file size.
   */
  @JvmField val size: Int = 0,
)

@Serializable
data class ModuleLibraryFile(
  /**
   * The file name.
   */
  @JvmField val name: String,

  /**
   * The file size.
   */
  @JvmField val size: Int = 0,
)

@Serializable
data class PluginContentReport(
  @JvmField val mainModule: String,
  @JvmField val os: String? = null,
  @JvmField val content: List<FileEntry> = emptyList(),
)

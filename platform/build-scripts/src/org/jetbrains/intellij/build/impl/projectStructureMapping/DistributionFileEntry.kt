// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import java.nio.file.Path

internal data class ContentReport(
  @JvmField val platform: List<DistributionFileEntry>,
  @JvmField val bundledPlugins: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>,
  @JvmField val nonBundledPlugins: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>,
) {
  fun combined(): Sequence<DistributionFileEntry> {
    return sequence {
      yieldAll(platform)
      yieldAll(bundledPlugins.flatMap { it.second })
      yieldAll(nonBundledPlugins.flatMap { it.second })
    }
  }
}

sealed interface DistributionFileEntry {
  /**
   * Path to a file in IDE distribution
   */
  val path: Path

  val relativeOutputFile: String?

  /**
   * Type of the element in the project configuration which was copied to [.path]
   */
  val type: String

  val hash: Long
}

sealed interface LibraryFileEntry : DistributionFileEntry {
  val libraryFile: Path?
  val size: Int
}

internal data class CustomAssetEntry(
  override val path: Path,
  override val hash: Long,
) : DistributionFileEntry {
  override val type: String
    get() = "custom-asset"

  override val relativeOutputFile: String?
    get() = null
}

/**
 * Represents a file in module-level library
 */
internal data class ModuleLibraryFileEntry(
  override val path: Path,
  @JvmField val moduleName: String,
  @JvmField val libraryName: String,
  override val libraryFile: Path?,
  override val size: Int,
  override val hash: Long,
  override val relativeOutputFile: String?,
) : DistributionFileEntry, LibraryFileEntry {
  override val type: String
    get() = "module-library-file"
}

/**
 * Represents test classes of a module
 */
internal data class ModuleTestOutputEntry(override val path: Path, @JvmField val moduleName: String) : DistributionFileEntry {
  override val relativeOutputFile: String?
    get() = null

  override val type: String
    get() = "module-test-output"

  override val hash: Long
    get() = 0
}

/**
 * Represents a project-level library
 */
internal data class ProjectLibraryEntry(
  override val path: Path,
  @JvmField val data: ProjectLibraryData,
  override val libraryFile: Path?,
  override val hash: Long,
  override val size: Int,
  override val relativeOutputFile: String?,
) : DistributionFileEntry, LibraryFileEntry {
  override val type: String
    get() = "project-library" }

/**
 * Represents production classes of a module
 */
data class ModuleOutputEntry(
  override val path: Path,
  @JvmField val moduleName: String,
  @JvmField val size: Int,
  override val hash: Long,
  override val relativeOutputFile: String,
  @JvmField val reason: String? = null,
) : DistributionFileEntry {
  override val type: String
    get() = "module-output"
}
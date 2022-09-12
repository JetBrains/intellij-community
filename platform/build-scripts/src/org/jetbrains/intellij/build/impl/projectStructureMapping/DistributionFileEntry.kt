// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import org.jetbrains.intellij.build.impl.ProjectLibraryData
import java.nio.file.Path

sealed interface DistributionFileEntry {
  /**
   * Path to a file in IDE distribution
   */
  val path: Path

  /**
   * Type of the element in the project configuration which was copied to [.path]
   */
  val type: String

  fun changePath(newFile: Path): DistributionFileEntry
}

interface LibraryFileEntry : DistributionFileEntry {
  val libraryFile: Path?
  val size: Int
}

/**
 * Represents a file in module-level library
 */
internal class ModuleLibraryFileEntry(override val path: Path,
                                      @JvmField val moduleName: String,
                                      override val libraryFile: Path?,
                                      override val size: Int) : DistributionFileEntry, LibraryFileEntry {
  override val type: String
    get() = "module-library-file"

  override fun changePath(newFile: Path) = ModuleLibraryFileEntry(newFile, moduleName, libraryFile, size)
}

/**
 * Represents test classes of a module
 */
internal class ModuleTestOutputEntry(override val path: Path, @JvmField val moduleName: String) : DistributionFileEntry {
  override val type: String
    get() = "module-test-output"

  override fun changePath(newFile: Path) = ModuleTestOutputEntry(newFile, moduleName)
}

/**
 * Represents a project-level library
 */
internal class ProjectLibraryEntry(
  override val path: Path,
  @JvmField val data: ProjectLibraryData,
  override val libraryFile: Path?,
  override val size: Int
) : DistributionFileEntry, LibraryFileEntry {
  override val type: String
    get() = "project-library"

  override fun changePath(newFile: Path) = ProjectLibraryEntry(newFile, data, libraryFile, size)

  override fun toString() = "ProjectLibraryEntry(data='$data\', libraryFile=$libraryFile, size=$size)"
}

/**
 * Represents production classes of a module
 */
class ModuleOutputEntry(
  override val path: Path,
  @JvmField val moduleName: String,
  @JvmField val size: Int,
  @JvmField val reason: String? = null
) : DistributionFileEntry {
  override val type: String
    get() = "module-output"

  override fun changePath(newFile: Path) = ModuleOutputEntry(newFile, moduleName, size, reason)
}
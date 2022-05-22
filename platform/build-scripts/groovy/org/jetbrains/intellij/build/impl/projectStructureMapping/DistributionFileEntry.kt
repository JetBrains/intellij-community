// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import org.jetbrains.intellij.build.impl.ProjectLibraryData
import java.nio.file.Path

/**
 * Base class for entries in [ProjectStructureMapping].
 */
interface DistributionFileEntry {
  /**
   * Path to a file in IDE distribution
   */
  val path: Path

  /**
   * Type of the element in the project configuration which was copied to [.path]
   */
  val type: String
}

interface LibraryFileEntry {
  val libraryFile: Path?
  val size: Int
}

/**
 * Represents a file in module-level library
 */
class ModuleLibraryFileEntry(override val path: Path,
                             val moduleName: String,
                             override val libraryFile: Path?,
                             override val size: Int) : DistributionFileEntry, LibraryFileEntry {
  override val type: String
    get() = "module-library-file"
}

/**
 * Represents test classes of a module
 */
class ModuleTestOutputEntry(override val path: Path, val moduleName: String) : DistributionFileEntry {
  override val type: String
    get() = "module-test-output"
}

/**
 * Represents a project-level library
 */
class ProjectLibraryEntry(override val path: Path,
                          val data: ProjectLibraryData,
                          override val libraryFile: Path?,
                          override val size: Int) : DistributionFileEntry, LibraryFileEntry {
  override val type: String
    get() = "project-library"

  override fun toString() = "ProjectLibraryEntry(data='$data\', libraryFile=$libraryFile, size=$size)"
}

/**
 * Represents production classes of a module
 */
class ModuleOutputEntry @JvmOverloads constructor(override val path: Path,
                                                  val moduleName: String,
                                                  val size: Int,
                                                  val reason: String? = null) : DistributionFileEntry {
  override val type: String
    get() = "module-output"
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

enum class LibraryPackMode {
  // all JARs of the library are merged into a JAR named after the library
  STANDALONE_MERGED,
  // all JARs of the library are included in the dist separately
  STANDALONE_SEPARATE,
}

/**
 * @param owner the platform product module ([ModuleItem]) whose jar carries the project library, in most cases a library
 * module (`intellij.libraries.*`) that exports it. `null` when a layout call packs the library.
 */
class ProjectLibraryData(
  @JvmField val libraryName: String,
  @JvmField val packMode: LibraryPackMode = LibraryPackMode.STANDALONE_MERGED,
  @JvmField val reason: String?,
  @JvmField val owner: ModuleItem?,
  @JvmField val outPath: String? = null,
) {
  init {
    require(outPath == null || !outPath.isBlank()) {
      "Empty outPath is not allowed, please pass null. libraryName=$libraryName"
    }
  }

  override fun equals(other: Any?): Boolean {
    return this === other ||
           javaClass == other?.javaClass && libraryName == (other as ProjectLibraryData).libraryName
  }

  override fun hashCode() = libraryName.hashCode()

  override fun toString() = "ProjectLibraryData(name=$libraryName, packMode=$packMode, relativeOutputPath=$outPath, reason=$reason)"
}

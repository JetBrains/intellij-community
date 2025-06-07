// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.util.TreeMap

enum class LibraryPackMode {
  // merged into some uber-JAR
  MERGED,
  // all JARs of the library are merged into a JAR named after the library
  STANDALONE_MERGED,
  // all JARs of the library are included in the dist separately
  STANDALONE_SEPARATE,
  // all JARs of the library are included in the dist separately with transformed file names (version suffix is removed)
  STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
}

class ProjectLibraryData(
  @JvmField val libraryName: String,
  @JvmField val packMode: LibraryPackMode = LibraryPackMode.STANDALONE_MERGED,
  @JvmField val reason: String?,
  @JvmField val outPath: String? = null,
) {
  init {
    require(outPath == null || !outPath.isBlank()) {
      "Empty outPath is not allowed, please pass null. libraryName=$libraryName"
    }
  }

  // plugin to a list of modules that uses the library
  @JvmField
  val dependentModules: MutableMap<String, MutableList<String>> = TreeMap()

  override fun equals(other: Any?): Boolean {
    return this === other ||
           javaClass == other?.javaClass && libraryName == (other as ProjectLibraryData).libraryName
  }

  override fun hashCode() = libraryName.hashCode()

  override fun toString() = "ProjectLibraryData(name=$libraryName, packMode=$packMode, relativeOutputPath=$outPath, reason=$reason)"
}

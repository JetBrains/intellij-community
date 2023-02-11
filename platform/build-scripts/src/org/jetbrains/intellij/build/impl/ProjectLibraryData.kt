// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.util.*

enum class LibraryPackMode {
  // merged into some uber jar
  MERGED,
  // all JARs of the library is merged into JAR named after the library
  STANDALONE_MERGED,
  // all JARs of the library is included into dist separate
  STANDALONE_SEPARATE,
  // all JARs of the library is included into dist separate with transformed file names (version suffix is removed)
  STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
}

class ProjectLibraryData(
  @JvmField val libraryName: String,
  @JvmField val packMode: LibraryPackMode,
  @JvmField val outPath: String? = null,
  @JvmField val reason: String? = null,
) {
  init {
    require(outPath == null || !outPath.isBlank()) {
      "Empty outPath is not allowed, please pass null. libraryName=$libraryName"
    }
  }

  // plugin to a list of modules that uses the library
  val dependentModules: MutableMap<String, MutableList<String>> = TreeMap()

  override fun toString() = "ProjectLibraryData(name=$libraryName, packMode=$packMode, relativeOutputPath=$outPath)"

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (javaClass != other?.javaClass) {
      return false
    }
    other as ProjectLibraryData
    return libraryName == other.libraryName
  }

  override fun hashCode() = libraryName.hashCode()
}

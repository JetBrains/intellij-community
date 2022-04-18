// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.util.*

class ProjectLibraryData @JvmOverloads constructor(
  val libraryName: String,
  val outPath: String?,
  val packMode: PackMode,
  val reason: String? = null,
) {
  // plugin to list of modules that uses the library
  val dependentModules: MutableMap<String, List<String>> = TreeMap()

  enum class PackMode {
    // merged into some uber jar
    MERGED,
    // all JARs of the library is merged into JAR named after the library
    STANDALONE_MERGED,
    // all JARs of the library is included into dist separate
    STANDALONE_SEPARATE,
    // all JARs of the library is included into dist separate with transformed file names (version suffix is removed)
    STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
  }

  override fun toString(): String {
    return "ProjectLibraryData(name=$libraryName, packMode=$packMode, relativeOutputPath=$outPath)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ProjectLibraryData

    if (libraryName != other.libraryName) return false

    return true
  }

  override fun hashCode(): Int {
    return libraryName.hashCode()
  }
}

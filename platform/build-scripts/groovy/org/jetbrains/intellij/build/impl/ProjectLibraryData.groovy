// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
final class ProjectLibraryData {
  final String libraryName
  final String relativeOutputPath
  final PackMode packMode
  // plugin to list of modules that uses the library
  final Map<String, List<String>> dependentModules = new TreeMap<>()

  enum PackMode {
    // merged into some uber jar
    MERGED,
    // all JARs of the library is merged into JAR named after the library
    STANDALONE_MERGED,
    // all JARs of the library is included into dist separate
    STANDALONE_SEPARATE,
    // all JARs of the library is included into dist separate with transformed file names (version suffix is removed)
    STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
  }

  ProjectLibraryData(@NotNull String libraryName, String relativeOutputPath, @NotNull PackMode packMode) {
    this.libraryName = libraryName
    this.relativeOutputPath = relativeOutputPath
    this.packMode = packMode
  }

  boolean equals(o) {
    if (this.is(o)) return true
    if (!(o instanceof ProjectLibraryData)) return false

    ProjectLibraryData data = (ProjectLibraryData)o
    if (libraryName != data.libraryName) return false

    return true
  }

  int hashCode() {
    return libraryName.hashCode()
  }

  @Override
  String toString() {
    return "ProjectLibraryData(name=$libraryName, packMode=$packMode, relativeOutputPath=$relativeOutputPath)"
  }
}

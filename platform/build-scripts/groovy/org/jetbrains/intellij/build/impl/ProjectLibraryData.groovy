// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.Strings
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

@CompileStatic
final class ProjectLibraryData {
  final String libraryName
  final String outPath
  final PackMode packMode
  // plugin to list of modules that uses the library
  final Map<String, List<String>> dependentModules = new TreeMap<>()

  final @Nullable String reason

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

  ProjectLibraryData(@NotNull String libraryName, @Nullable String outPath, @NotNull PackMode packMode) {
    this(libraryName, outPath, packMode, null)
  }

  ProjectLibraryData(@NotNull String libraryName, @Nullable String outPath, @NotNull PackMode packMode, @Nullable String reason) {
    this.libraryName = libraryName
    this.outPath = Strings.nullize(outPath)
    this.packMode = packMode
    this.reason = reason
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
    return "ProjectLibraryData(name=$libraryName, packMode=$packMode, relativeOutputPath=$outPath)"
  }
}

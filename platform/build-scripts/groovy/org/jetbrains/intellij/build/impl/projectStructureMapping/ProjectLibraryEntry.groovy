// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.impl.ProjectLibraryData

import java.nio.file.Path

/**
 * Represents a project-level library
 */
@CompileStatic
final class ProjectLibraryEntry extends DistributionFileEntry implements DistributionFileEntry.LibraryFileEntry {
  final String libraryName
  final Path libraryFile
  final int size
  final @Nullable ProjectLibraryData data
  final @Nullable String reason

  ProjectLibraryEntry(Path path, String libraryName, Path libraryFile, int size) {
    this(path, libraryName, libraryFile, null, null, size)
  }

  ProjectLibraryEntry(Path path,
                      String libraryName,
                      Path libraryFile,
                      @Nullable ProjectLibraryData data,
                      @Nullable String reason,
                      int size) {
    super(path, "project-library")

    this.libraryName = libraryName
    this.libraryFile = libraryFile
    this.data = data
    this.reason = reason
    this.size = size
  }

  @Override
  String toString() {
    return "ProjectLibraryEntry(" +
           "libraryName='" + libraryName + '\'' +
           ", libraryFile=" + libraryFile +
           ", size=" + size +
           ')'
  }
}

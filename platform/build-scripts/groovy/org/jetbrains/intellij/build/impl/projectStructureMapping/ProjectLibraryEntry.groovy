// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.impl.ProjectLibraryData

import java.nio.file.Path

/**
 * Represents a project-level library
 */
@CompileStatic
final class ProjectLibraryEntry extends DistributionFileEntry implements DistributionFileEntry.LibraryFileEntry {
  final ProjectLibraryData data
  final Path libraryFile
  final int size

  ProjectLibraryEntry(Path path, @NotNull ProjectLibraryData data, Path libraryFile, int size) {
    super(path, "project-library")

    this.libraryFile = libraryFile
    this.data = data
    this.size = size
  }

  @Override
  String toString() {
    return "ProjectLibraryEntry(" +
           "data='" + data + '\'' +
           ", libraryFile=" + libraryFile +
           ", size=" + size +
           ')'
  }
}

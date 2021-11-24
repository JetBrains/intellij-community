// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Represents a project-level library
 */
@CompileStatic
final class ProjectLibraryEntry extends DistributionFileEntry implements DistributionFileEntry.LibraryFileEntry {
  final String libraryName
  final Path libraryFile
  final int size

  ProjectLibraryEntry(Path path, String libraryName, Path libraryFile, int size) {
    super(path, "project-library")

    this.libraryName = libraryName
    this.libraryFile = libraryFile
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

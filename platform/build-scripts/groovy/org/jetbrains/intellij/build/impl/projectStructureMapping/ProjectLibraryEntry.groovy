// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.AutoClone
import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Represents a project-level library
 */
@CompileStatic
@AutoClone
final class ProjectLibraryEntry extends DistributionFileEntry {
  final String libraryName
  final transient Path libraryFile
  final int fileSize

  ProjectLibraryEntry(String path, String libraryName, Path libraryFile, int fileSize) {
    super(path, "project-library")
    this.libraryName = libraryName
    this.libraryFile = libraryFile
    this.fileSize = fileSize
  }
}

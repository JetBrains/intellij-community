// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.AutoClone
import groovy.transform.CompileStatic

/**
 * Represents a project-level library
 */
@CompileStatic
@AutoClone
final class ProjectLibraryEntry extends DistributionFileEntry {
  String libraryName
  String libraryFilePath

  ProjectLibraryEntry(String path, String libraryName, String libraryFilePath) {
    super(path, "project-library")
    this.libraryName = libraryName
    this.libraryFilePath = libraryFilePath
  }
}

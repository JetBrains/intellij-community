// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic

@CompileStatic
final class ProjectLibraryData {
  final String libraryName
  final String relativeOutputPath
  /**
   * Do not merge library JAR file into uber JAR.
   */
  final boolean standalone

  ProjectLibraryData(String libraryName, String relativeOutputPath, boolean standalone) {
    this.libraryName = libraryName
    this.relativeOutputPath = relativeOutputPath
    this.standalone = standalone
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
}

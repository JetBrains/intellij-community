// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.AutoClone
import groovy.transform.CompileStatic

/**
 * Represents a file in module-level library
 */
@CompileStatic
@AutoClone
class ModuleLibraryFileEntry extends DistributionFileEntry {
  /**
   * Path to the library file in the project sources, may use the standard $PROJECT_DIR$ and $MAVEN_REPOSITORY$ path macros
   */
  String filePath
  String libraryFilePath

  ModuleLibraryFileEntry(String path, String filePath, String libraryFilePath) {
    super(path, "module-library-file")
    this.filePath = filePath
    this.libraryFilePath = libraryFilePath
  }
}

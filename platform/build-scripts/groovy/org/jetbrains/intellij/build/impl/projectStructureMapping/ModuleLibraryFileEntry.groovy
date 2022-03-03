// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Represents a file in module-level library
 */
@CompileStatic
final class ModuleLibraryFileEntry extends DistributionFileEntry implements DistributionFileEntry.LibraryFileEntry {
  final String moduleName
  final Path libraryFile
  final int size

  ModuleLibraryFileEntry(Path path, String moduleName, Path libraryFile, int size) {
    super(path, "module-library-file")

    this.moduleName = moduleName
    this.libraryFile = libraryFile
    this.size = size
  }
}

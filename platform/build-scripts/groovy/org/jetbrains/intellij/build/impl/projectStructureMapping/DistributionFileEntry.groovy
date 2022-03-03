// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Base class for entries in {@link ProjectStructureMapping}.
 */
@CompileStatic
abstract class DistributionFileEntry {
  /**
   * Path to a file in IDE distribution
   */
  final Path path

  /**
   * Type of the element in the project configuration which was copied to {@link #path}
   */
  final String type

  DistributionFileEntry(Path path, String type) {
    this.path = path
    this.type = type
  }

  @CompileStatic
  static interface LibraryFileEntry {
    Path getLibraryFile()

    int getSize()
  }
}

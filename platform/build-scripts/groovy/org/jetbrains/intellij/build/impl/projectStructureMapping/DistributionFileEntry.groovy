// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.AutoClone
import groovy.transform.CompileStatic

/**
 * Base class for entries in {@link ProjectStructureMapping}.
 */
@CompileStatic
@AutoClone
abstract class DistributionFileEntry {
  /**
   * Path to a file in IDE distribution (relative to IDE home directory)
   */
  String path
  /**
   * Type of the element in the project configuration which was copied to {@link #path}
   */
  String type

  DistributionFileEntry(String path, String type) {
    this.path = path
    this.type = type
  }
}

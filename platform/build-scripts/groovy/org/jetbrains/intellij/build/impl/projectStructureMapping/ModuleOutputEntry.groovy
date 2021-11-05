// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Represents production classes of a module
 */
@CompileStatic
final class ModuleOutputEntry extends DistributionFileEntry {
  final String moduleName
  final int size

  ModuleOutputEntry(Path path, String moduleName, int size) {
    super(path, "module-output")

    this.moduleName = moduleName
    this.size = size
  }
}

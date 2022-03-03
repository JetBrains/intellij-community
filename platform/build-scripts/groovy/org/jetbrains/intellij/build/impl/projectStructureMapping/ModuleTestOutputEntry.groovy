// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Represents test classes of a module
 */
@CompileStatic
final class ModuleTestOutputEntry extends DistributionFileEntry {
  final String moduleName

  ModuleTestOutputEntry(Path path, String moduleName) {
    super(path, "module-test-output")

    this.moduleName = moduleName
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.AutoClone
import groovy.transform.CompileStatic

/**
 * Represents test classes of a module
 */
@CompileStatic
@AutoClone
final class ModuleTestOutputEntry extends DistributionFileEntry {
  String moduleName

  ModuleTestOutputEntry(String path, String moduleName) {
    super(path, "module-test-output")
    this.moduleName = moduleName
  }
}

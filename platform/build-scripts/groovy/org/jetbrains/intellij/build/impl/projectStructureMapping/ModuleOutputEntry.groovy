// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.AutoClone
import groovy.transform.CompileStatic

/**
 * Represents production classes of a module
 */
@CompileStatic
@AutoClone
class ModuleOutputEntry extends DistributionFileEntry {
  String moduleName

  ModuleOutputEntry(String path, String moduleName) {
    super(path, "module-output")
    this.moduleName = moduleName
  }
}

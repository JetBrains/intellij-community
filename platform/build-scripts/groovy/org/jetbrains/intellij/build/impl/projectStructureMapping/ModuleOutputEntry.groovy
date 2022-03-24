// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Represents production classes of a module
 */
@CompileStatic
final class ModuleOutputEntry extends DistributionFileEntry {
  public final String moduleName
  public final int size
  public final String reason

  ModuleOutputEntry(Path path, String moduleName, int size, String reason = null) {
    super(path, "module-output")

    this.moduleName = moduleName
    this.size = size
    this.reason = reason
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping;

import java.nio.file.Path;

/**
 * Represents test classes of a module
 */
public final class ModuleTestOutputEntry extends DistributionFileEntry {
  public ModuleTestOutputEntry(Path path, String moduleName) {
    super(path, "module-test-output");

    this.moduleName = moduleName;
  }

  public final String getModuleName() {
    return moduleName;
  }

  private final String moduleName;
}

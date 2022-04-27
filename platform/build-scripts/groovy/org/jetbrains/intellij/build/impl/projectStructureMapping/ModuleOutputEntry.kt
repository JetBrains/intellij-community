// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping;

import java.nio.file.Path;

/**
 * Represents production classes of a module
 */
public final class ModuleOutputEntry extends DistributionFileEntry {
  public ModuleOutputEntry(Path path, String moduleName, int size, String reason) {
    super(path, "module-output");

    this.moduleName = moduleName;
    this.size = size;
    this.reason = reason;
  }

  public ModuleOutputEntry(Path path, String moduleName, int size) {
    this(path, moduleName, size, null);
  }

  public final String moduleName;
  public final int size;
  public final String reason;
}

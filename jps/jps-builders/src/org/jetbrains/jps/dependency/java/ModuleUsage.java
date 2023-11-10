// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

public final class ModuleUsage extends JvmElementUsage {

  public ModuleUsage(@NotNull String moduleName) {
    this(new JvmNodeReferenceID(moduleName));
  }

  public ModuleUsage(@NotNull JvmNodeReferenceID modId) {
    super(modId);
  }

  public ModuleUsage(DataInput in) throws IOException {
    super(in);
  }

  public String getModuleName() {
    return getElementOwner().getNodeName();
  }
}

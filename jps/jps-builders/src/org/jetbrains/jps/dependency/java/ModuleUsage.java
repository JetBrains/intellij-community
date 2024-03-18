// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public final class ModuleUsage extends JvmElementUsage {

  public ModuleUsage(@NotNull String moduleName) {
    this(new JvmNodeReferenceID(moduleName));
  }

  public ModuleUsage(@NotNull JvmNodeReferenceID modId) {
    super(modId);
  }

  public ModuleUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  public String getModuleName() {
    return getElementOwner().getNodeName();
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 1;
  }
}

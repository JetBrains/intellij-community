// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;

public final class ModuleUsage extends JvmElementUsage {

  public ModuleUsage(@NotNull String moduleName) {
    super(new JvmNodeReferenceID(moduleName));
  }
  
  public String getModuleName() {
    return ((JvmNodeReferenceID)getElementOwner()).getNodeName();
  }
}

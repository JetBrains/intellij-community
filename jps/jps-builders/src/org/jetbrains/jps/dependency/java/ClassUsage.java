// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;

public class ClassUsage extends JvmElementUsage {

  public ClassUsage(@NotNull String className) {
    super(new JvmNodeReferenceID(className));
  }

  public String getClassName() {
    return ((JvmNodeReferenceID)getElementOwner()).getNodeName();
  }

}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public class ClassUsage extends JvmElementUsage {

  public ClassUsage(@NotNull String className) {
    this(new JvmNodeReferenceID(className));
  }
  
  public ClassUsage(@NotNull JvmNodeReferenceID id) {
    super(id);
  }

  public ClassUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  public String getClassName() {
    return getElementOwner().getNodeName();
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 10;
  }
}

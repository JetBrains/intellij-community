// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public final class ClassAsGenericBoundUsage extends ClassUsage {

  public ClassAsGenericBoundUsage(@NotNull String className) {
    super(className);
  }
  
  public ClassAsGenericBoundUsage(@NotNull JvmNodeReferenceID clsId) {
    super(clsId);
  }

  public ClassAsGenericBoundUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 3;
  }
}

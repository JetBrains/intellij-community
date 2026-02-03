// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public final class ClassPermitsUsage extends ClassUsage {

  public ClassPermitsUsage(@NotNull String className) {
    super(className);
  }

  public ClassPermitsUsage(@NotNull JvmNodeReferenceID clsId) {
    super(clsId);
  }

  public ClassPermitsUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 4;
  }
}

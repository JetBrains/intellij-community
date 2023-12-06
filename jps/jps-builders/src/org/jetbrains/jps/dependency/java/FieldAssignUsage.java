// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.GraphDataInput;

import java.io.IOException;

public final class FieldAssignUsage extends FieldUsage{

  public FieldAssignUsage(String className, String name, String descriptor) {
    super(className, name, descriptor);
  }

  public FieldAssignUsage(GraphDataInput in) throws IOException {
    super(in);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 2;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;

import java.io.IOException;

public class FieldUsage extends MemberUsage{

  private final String myDescriptor;

  public FieldUsage(String className, String name, String descriptor) {
    super(className, name);
    myDescriptor = descriptor;
  }

  public FieldUsage(JvmNodeReferenceID clsId, String name, String descriptor) {
    super(clsId, name);
    myDescriptor = descriptor;
  }

  public FieldUsage(@NotNull JvmNodeReferenceID owner, GraphDataInput in) throws IOException {
    super(owner, in);
    myDescriptor = in.readUTF();
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    out.writeUTF(myDescriptor);
  }

  public String getDescriptor() {
    return myDescriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    final FieldUsage that = (FieldUsage)o;

    if (!myDescriptor.equals(that.myDescriptor)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myDescriptor.hashCode();
    return result;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;

import java.io.IOException;

public final class MethodUsage extends MemberUsage{

  private final String myDescriptor;

  public MethodUsage(String className, String name, String descriptor) {
    super(className, name);
    myDescriptor = descriptor;
  }

  public MethodUsage(JvmNodeReferenceID clsId, String name, String descriptor) {
    super(clsId, name);
    myDescriptor = descriptor;
  }

  public MethodUsage(@NotNull JvmNodeReferenceID owner, GraphDataInput in) throws IOException {
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

    final MethodUsage that = (MethodUsage)o;

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

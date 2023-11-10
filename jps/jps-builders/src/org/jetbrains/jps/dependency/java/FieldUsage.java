// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import java.io.DataInput;
import java.io.DataOutput;
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

  public FieldUsage(DataInput in) throws IOException {
    super(in);
    myDescriptor = in.readUTF();
  }

  @Override
  public void write(DataOutput out) throws IOException {
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

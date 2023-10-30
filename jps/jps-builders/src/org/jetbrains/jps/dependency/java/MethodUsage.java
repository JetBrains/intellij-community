// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

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

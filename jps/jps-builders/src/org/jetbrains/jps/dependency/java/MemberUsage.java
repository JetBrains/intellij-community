// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

public abstract class MemberUsage extends JvmElementUsage {

  private final String myName;

  protected MemberUsage(String className, String name) {
    this(new JvmNodeReferenceID(className), name);
  }

  protected MemberUsage(JvmNodeReferenceID clsId, String name) {
    super(clsId);
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    final MemberUsage that = (MemberUsage)o;

    if (!myName.equals(that.myName)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }
}

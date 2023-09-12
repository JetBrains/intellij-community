// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.impl.StringReferenceID;

public class AnnotationUsage extends JvmElementUsage {

  private final TypeRepr.ClassType myClassType;
  private final Iterable<String> myUserArgNames;
  private final Iterable<ElemType> myTargets;

  public AnnotationUsage(TypeRepr.ClassType classType, Iterable<String> userArgNames, Iterable<ElemType> targets) {
    super(new StringReferenceID(classType.getJvmName()));
    myClassType = classType;
    myUserArgNames = userArgNames;
    myTargets = targets;
  }

  public TypeRepr.ClassType getClassType() {
    return myClassType;
  }

  public Iterable<String> getUserArgNames() {
    return myUserArgNames;
  }

  public Iterable<ElemType> getTargets() {
    return myTargets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final AnnotationUsage that = (AnnotationUsage)o;

    if (!myClassType.equals(that.myClassType)) {
      return false;
    }
    if (!myUserArgNames.equals(that.myUserArgNames)) {
      return false;
    }
    if (!myTargets.equals(that.myTargets)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myClassType.hashCode();
    result = 31 * result + myUserArgNames.hashCode();
    result = 31 * result + myTargets.hashCode();
    return result;
  }
}

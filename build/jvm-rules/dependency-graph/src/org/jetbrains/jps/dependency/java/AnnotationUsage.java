// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.impl.RW;
import org.jetbrains.jps.util.Iterators;

import java.io.IOException;

public final class AnnotationUsage extends JvmElementUsage {

  private final TypeRepr.ClassType myClassType;
  private final Iterable<String> myUsedArgNames;
  private final Iterable<ElemType> myTargets;

  public AnnotationUsage(TypeRepr.ClassType classType, Iterable<String> usedArgNames, Iterable<ElemType> targets) {
    super(new JvmNodeReferenceID(classType.getJvmName()));
    myClassType = classType;
    myUsedArgNames = usedArgNames;
    myTargets = targets;
  }

  public AnnotationUsage(GraphDataInput in) throws IOException {
    super(in);
    myClassType = new TypeRepr.ClassType(getElementOwner().getNodeName());
    myUsedArgNames = RW.readCollection(in, () -> in.readUTF());
    myTargets = RW.readCollection(in, ()-> ElemType.fromOrdinal(in.readInt()));
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    RW.writeCollection(out, myUsedArgNames, s -> out.writeUTF(s));
    RW.writeCollection(out, myTargets, t -> out.writeInt(t.ordinal()));
  }

  public TypeRepr.ClassType getClassType() {
    return myClassType;
  }

  public Iterable<String> getUsedArgNames() {
    return myUsedArgNames;
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
    if (!Iterators.equals(myUsedArgNames, that.myUsedArgNames)) {
      return false;
    }
    if (!Iterators.equals(myTargets, that.myTargets)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myClassType.hashCode();
    result = 31 * result + Iterators.hashCode(myUsedArgNames);
    result = 31 * result + Iterators.hashCode(myTargets);
    return result;
  }
}

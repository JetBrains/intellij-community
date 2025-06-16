// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.diff.DiffCapable;

import java.io.IOException;

public final class ParamAnnotation extends AnnotationInstance implements DiffCapable<ParamAnnotation, ParamAnnotation.Diff>{
  public final int paramIndex;

  public ParamAnnotation(int paramIndex, @NotNull TypeRepr.ClassType type, Object contentHash) {
    super(type, contentHash);
    this.paramIndex = paramIndex;
  }

  public ParamAnnotation(GraphDataInput in) throws IOException {
    super(in);
    paramIndex = in.readInt();
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    out.writeInt(paramIndex);
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof ParamAnnotation && paramIndex == ((ParamAnnotation)other).paramIndex && getAnnotationClass().equals(((ParamAnnotation)other).getAnnotationClass());
  }

  @Override
  public int diffHashCode() {
    return 31 * getAnnotationClass().hashCode() + paramIndex;
  }

  @Override
  public Diff difference(ParamAnnotation past) {
    return new Diff(past);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ParamAnnotation that = (ParamAnnotation)o;
    return paramIndex == that.paramIndex;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + paramIndex;
    return result;
  }

  public class Diff extends AnnotationInstance.Diff<ParamAnnotation> {
    public Diff(ParamAnnotation past) {
      super(past);
    }
  }
}

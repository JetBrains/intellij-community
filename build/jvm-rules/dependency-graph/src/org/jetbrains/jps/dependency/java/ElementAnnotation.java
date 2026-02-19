// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.java.TypeRepr.ClassType;

import java.io.IOException;

public final class ElementAnnotation extends AnnotationInstance implements DiffCapable<ElementAnnotation, ElementAnnotation.Diff>{

  public ElementAnnotation(@NotNull ClassType annotClass, Object contentHash) {
    super(annotClass, contentHash);
  }

  public ElementAnnotation(GraphDataInput in) throws IOException {
    super(in);
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof ElementAnnotation && getAnnotationClass().equals(((ElementAnnotation)other).getAnnotationClass());
  }

  @Override
  public int diffHashCode() {
    return getAnnotationClass().hashCode();
  }

  @Override
  public Diff difference(ElementAnnotation past) {
    return new Diff(past);
  }
  
  public class Diff extends AnnotationInstance.Diff<ElementAnnotation> {
    public Diff(ElementAnnotation past) {
      super(past);
    }
  }
}

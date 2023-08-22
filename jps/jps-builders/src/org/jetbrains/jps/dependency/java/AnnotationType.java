// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

import java.lang.annotation.RetentionPolicy;

public class AnnotationType extends ObjectType<AnnotationType, AnnotationType.Diff> {

  private final Iterable<ElemType> myAnnotationTargets;
  private final RetentionPolicy myRetentionPolicy;

  public AnnotationType(JVMFlags flags, String signature, String name, String outFilePath,
                        @NotNull Iterable<TypeRepr.ClassType> annotations,
                        @NotNull Iterable<Field> fields,
                        @NotNull Iterable<Method> methods,
                        @NotNull Iterable<Usage> usages,
                        @NotNull Iterable<ElemType> annotationTargets,
                        @NotNull RetentionPolicy retentionPolicy
                            ) {
    super(flags, signature, name, outFilePath, fields, methods, annotations, usages);
    myAnnotationTargets = annotationTargets;
    myRetentionPolicy = retentionPolicy;
  }

  public Iterable<ElemType> getAnnotationTargets() {
    return myAnnotationTargets;
  }

  public RetentionPolicy getRetentionPolicy() {
    return myRetentionPolicy;
  }

  @Override
  public Diff difference(AnnotationType other) {
    return new Diff(other);
  }

  public static class Diff implements Difference {

    public Diff(AnnotationType other) {
      // todo: diff necessary data
    }

    @Override
    public boolean unchanged() {
      return false;
    }

  }

}

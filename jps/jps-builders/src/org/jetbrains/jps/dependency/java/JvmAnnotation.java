// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

public class JvmAnnotation extends JvmObjectType<JvmAnnotation, JvmAnnotation.Diff> {

  private final Iterable<ElemType> myAnnotationTargets;
  private final RetentionPolicy myRetentionPolicy;

  public JvmAnnotation(JVMFlags flags, String signature, String name, String outFilePath,
                       @NotNull Iterable<TypeRepr.ClassType> annotations,
                       @NotNull Iterable<JvmField> fields,
                       @NotNull Iterable<JvmMethod> methods,
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
  public Diff difference(JvmAnnotation past) {
    return new Diff(past);
  }

  public class Diff extends JvmObjectType<JvmAnnotation, JvmAnnotation.Diff>.Diff<JvmAnnotation> {

    public Diff(JvmAnnotation past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && !retentionPolicyChanged() && annotations().unchanged();
    }

    public boolean retentionPolicyChanged() {
      return !Objects.equals(myPast.getRetentionPolicy(), getRetentionPolicy());
    }

    public Specifier<ElemType, ?> annotationTargets() {
      return Difference.diff(myPast.getAnnotationTargets(), getAnnotationTargets());
    }

  }

}

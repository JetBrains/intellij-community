// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

public class JvmClass extends JVMClassNode<JvmClass, JvmClass.Diff> {
  private final String mySuperFqName;
  private final String myOuterFqName;
  private final Iterable<String> myInterfaces;
  private final Iterable<JvmField> myFields;
  private final Iterable<JvmMethod> myMethods;
  private final Iterable<ElemType> myAnnotationTargets;
  private final RetentionPolicy myRetentionPolicy;

  public JvmClass(
    JVMFlags flags, String signature, String fqName, String outFilePath,
    String superFqName,
    String outerFqName,
    Iterable<String> interfaces,
    Iterable<JvmField> fields,
    Iterable<JvmMethod> methods,
    Iterable<TypeRepr.ClassType> annotations,
    Iterable<ElemType> annotationTargets, RetentionPolicy retentionPolicy, Iterable<Usage> usages
    ) {
    
    super(flags, signature, fqName, outFilePath, annotations, usages);
    mySuperFqName = superFqName;
    myOuterFqName = outerFqName;
    myInterfaces = interfaces;
    myFields = fields;
    myMethods = methods;
    myAnnotationTargets = annotationTargets;
    myRetentionPolicy = retentionPolicy;
  }

  @NotNull
  public String getPackageName() {
    String name = getName();
    int index = name.lastIndexOf('/');
    return index >= 0? name.substring(0, index) : "";
  }

  public final boolean isAnonymous() {
    return getFlags().isAnonymous();
  }

  public String getSuperFqName() {
    return mySuperFqName;
  }

  public String getOuterFqName() {
    return myOuterFqName;
  }

  public Iterable<String> getInterfaces() {
    return myInterfaces;
  }

  public Iterable<String> getSuperTypes() {
    return Iterators.flat(Iterators.asIterable(mySuperFqName), getInterfaces());
  }

  public Iterable<JvmField> getFields() {
    return myFields;
  }

  public Iterable<JvmMethod> getMethods() {
    return myMethods;
  }

  public Iterable<ElemType> getAnnotationTargets() {
    return myAnnotationTargets;
  }

  public RetentionPolicy getRetentionPolicy() {
    return myRetentionPolicy;
  }

  @Override
  public Diff difference(JvmClass past) {
    return new Diff(past);
  }

  public class Diff extends Proto.Diff<JvmClass> {

    public Diff(JvmClass past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return
        super.unchanged() &&
        !superClassChanged() &&
        !outerClassChanged() &&
        interfaces().unchanged() &&
        methods().unchanged() &&
        fields().unchanged() &&
        !retentionPolicyChanged() &&
        annotationTargets().unchanged();
    }

    public boolean superClassChanged() {
      return !Objects.equals(myPast.getSuperFqName(), getSuperFqName());
    }

    public boolean extendsAdded() {
      return "java/lang/Object".equals(myPast.getSuperFqName()) && superClassChanged();
    }

    public boolean extendsRemoved() {
      return "java/lang/Object".equals(getSuperFqName()) && superClassChanged();
    }

    public boolean outerClassChanged() {
      return !Objects.equals(myPast.getOuterFqName(), getOuterFqName());
    }

    public Specifier<String, ?> interfaces() {
      return Difference.diff(myPast.getInterfaces(), getInterfaces());
    }

    public Specifier<JvmMethod, JvmMethod.Diff> methods() {
      return Difference.deepDiff(myPast.getMethods(), getMethods());
    }

    public Specifier<JvmField, JvmField.Diff> fields() {
      return Difference.deepDiff(myPast.getFields(), getFields());
    }

    public boolean retentionPolicyChanged() {
      return !Objects.equals(myPast.getRetentionPolicy(), getRetentionPolicy());
    }

    public Specifier<ElemType, ?> annotationTargets() {
      return Difference.diff(myPast.getAnnotationTargets(), getAnnotationTargets());
    }
  }
}

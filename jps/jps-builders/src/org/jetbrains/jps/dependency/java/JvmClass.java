// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.Objects;

public class JvmClass extends JvmObjectType<JvmClass, JvmClass.Diff> {
  private final String mySuperFqName;
  private final String myOuterFqName;
  private final Iterable<String> myInterfaces;

  public JvmClass(
    JVMFlags flags, String signature, String fqName, String outFilePath,
    String superFqName,
    String outerFqName,
    Iterable<String> interfaces,
    Iterable<JvmField> fields,
    Iterable<JvmMethod> methods,
    Iterable<TypeRepr.ClassType> annotations,
    Iterable<Usage> usages
    ) {
    
    super(flags, signature, fqName, outFilePath, fields, methods, annotations, usages);
    mySuperFqName = superFqName;
    myOuterFqName = outerFqName;
    myInterfaces = interfaces;
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

  @Override
  public Diff difference(JvmClass past) {
    return new Diff(past);
  }

  public class Diff extends JvmObjectType<JvmClass, JvmClass.Diff>.Diff<JvmClass> {

    public Diff(JvmClass past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && !superClassChanged() && !outerClassChanged() && interfaces().unchanged();
    }

    public boolean superClassChanged() {
      return !Objects.equals(myPast.getSuperFqName(), getSuperFqName());
    }

    public boolean outerClassChanged() {
      return !Objects.equals(myPast.getOuterFqName(), getOuterFqName());
    }

    public Specifier<String, ?> interfaces() {
      return Difference.diff(myPast.getInterfaces(), getInterfaces());
    }

  }
}

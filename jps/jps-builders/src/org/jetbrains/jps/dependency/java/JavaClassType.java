// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

public class JavaClassType extends ObjectType<JavaClassType, JavaClassType.Diff> {
  private final String mySuperFqName;
  private final String myOuterFqName;
  private final Iterable<String> myInterfaces;

  public JavaClassType(
    JVMFlags flags, String signature, String fqName, String outFilePath,
    String superFqName,
    String outerFqName,
    Iterable<String> interfaces,
    Iterable<Field> fields,
    Iterable<Method> methods,
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
  public Diff difference(JavaClassType other) {
    return new Diff(other);
  }

  public static class Diff implements Difference {

    public Diff(JavaClassType other) {
      // todo: diff necessary data
    }

    @Override
    public boolean unchanged() {
      return false;
    }
    
  }
}

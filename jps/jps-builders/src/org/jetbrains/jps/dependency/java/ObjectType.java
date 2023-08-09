// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

public abstract class ObjectType<T extends ObjectType<T, D>, D extends Difference> extends JVMClassNode<T, D>{

  private final Iterable<Field> myFields;
  private final Iterable<Method> myMethods;

  public ObjectType(JVMFlags flags, String signature, String name, String outFilePath,
                    Iterable<Field> fields,
                    Iterable<Method> methods,
                    @NotNull Iterable<TypeRepr.ClassType> annotations,
                    @NotNull Iterable<Usage> usages) {
    super(flags, signature, name, outFilePath, annotations, usages);
    myFields = fields;
    myMethods = methods;
  }

  public Iterable<Field> getFields() {
    return myFields;
  }

  public Iterable<Method> getMethods() {
    return myMethods;
  }
}

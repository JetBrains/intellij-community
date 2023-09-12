// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

public abstract class JvmObjectType<T extends JvmObjectType<T, D>, D extends Difference> extends JVMClassNode<T, D>{

  private final Iterable<JvmField> myFields;
  private final Iterable<JvmMethod> myMethods;

  public JvmObjectType(JVMFlags flags, String signature, String name, String outFilePath,
                       Iterable<JvmField> fields,
                       Iterable<JvmMethod> methods,
                       @NotNull Iterable<TypeRepr.ClassType> annotations,
                       @NotNull Iterable<Usage> usages) {
    super(flags, signature, name, outFilePath, annotations, usages);
    myFields = fields;
    myMethods = methods;
  }

  public Iterable<JvmField> getFields() {
    return myFields;
  }

  public Iterable<JvmMethod> getMethods() {
    return myMethods;
  }

  public class Diff<V extends JvmObjectType<T, D>> extends Proto.Diff<V> {
    public Diff(V past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && methods().unchanged() && fields().unchanged();
    }

    public Specifier<JvmMethod, JvmMethod.Diff> methods() {
      return Difference.deepDiff(myPast.getMethods(), getMethods());
    }

    public Specifier<JvmField, JvmField.Diff> fields() {
      return Difference.deepDiff(myPast.getFields(), getFields());
    }
  }
}

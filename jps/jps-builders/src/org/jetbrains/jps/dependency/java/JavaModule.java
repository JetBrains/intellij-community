// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;

public class JavaModule extends JVMClassNode<JavaModule, JavaModule.Diff>{

  public JavaModule(JVMFlags flags, String signature, String name, String outFilePath, @NotNull Iterable<TypeRepr.ClassType> annotations, @NotNull Iterable<Usage> usages) {
    super(flags, signature, name, outFilePath, annotations, usages);
  }

  @Override
  public Diff difference(JavaModule other) {
    return new Diff(other);
  }

  public static class Diff implements Difference {

    public Diff(JavaModule other) {
      // todo: diff necessary data
    }

    @Override
    public boolean unchanged() {
      return false;
    }

  }

}

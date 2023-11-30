// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.Node;

import java.util.function.Predicate;

public class PackageConstraint implements Predicate<Node<?, ?>> {

  private final String myPackageName;

  public PackageConstraint(String packageName) {
    myPackageName = packageName;
  }

  @Override
  public boolean test(Node<?, ?> node) {
    return !(node instanceof JvmClass) || !myPackageName.equals(((JvmClass)node).getPackageName());
  }
}

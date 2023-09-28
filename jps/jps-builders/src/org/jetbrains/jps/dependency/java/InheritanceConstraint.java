// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.Node;

import java.util.HashSet;

public final class InheritanceConstraint extends PackageConstraint{
  private final Utils myUtils;
  private final String myRootClass;

  public InheritanceConstraint(Utils utils, JvmClass rootClass) {
    super(rootClass.getPackageName());
    myRootClass = rootClass.getName();
    myUtils = utils;
  }

  @Override
  public boolean test(Node<?, ?> node) {
    return super.test(node) && !myUtils.collectAllSupertypes(((JvmClass)node).getName(), new HashSet<>()).contains(myRootClass);
  }
}

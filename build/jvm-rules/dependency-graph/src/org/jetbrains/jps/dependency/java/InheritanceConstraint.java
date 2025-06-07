// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.Node;

public final class InheritanceConstraint extends PackageConstraint{
  private final Utils myUtils;
  private final JvmNodeReferenceID myRootClass;

  public InheritanceConstraint(Utils utils, JvmClass rootClass) {
    super(rootClass.getPackageName());
    myRootClass = rootClass.getReferenceID();
    myUtils = utils;
  }

  @Override
  public boolean test(Node<?, ?> node) {
    if (!super.test(node)) {
      return false;
    }
    if (node instanceof JvmClass) {
      for (JvmNodeReferenceID s : myUtils.allSupertypes(((JvmClass)node).getReferenceID())) {
        if (myRootClass.equals(s)) {
          return false;
        }
      }
    }
    return true;
  }
}

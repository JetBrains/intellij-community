/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.psi.PsiFile;

public class NamedPackageSetReference implements PackageSet {
  private String myName;

  public NamedPackageSetReference(String name) {
    myName = name;
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    NamedScope scope = holder.getScope(myName);
    return scope == null ? false : scope.getValue().contains(file, holder);
  }

  public PackageSet createCopy() {
    return new NamedPackageSetReference(myName);
  }

  public String getText() {
    return myName;
  }

  public int getNodePriority() {
    return 0;
  }
}
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.psi.PsiFile;

public class UnionPackageSet implements PackageSet {
  private PackageSet myFirstSet;
  private PackageSet mySecondSet;

  public UnionPackageSet(PackageSet set1, PackageSet set2) {
    myFirstSet = set1;
    mySecondSet = set2;
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    return myFirstSet.contains(file, holder) || mySecondSet.contains(file, holder);
  }

  public PackageSet createCopy() {
    return new UnionPackageSet(myFirstSet.createCopy(), mySecondSet.createCopy());
  }

  public int getNodePriority() {
    return 3;
  }

  public String getText() {
    return myFirstSet.getText() + " || " + mySecondSet.getText();
  }
}
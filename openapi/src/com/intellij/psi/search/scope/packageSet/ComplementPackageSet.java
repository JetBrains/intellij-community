/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.psi.PsiFile;

public class ComplementPackageSet implements PackageSet {
  private PackageSet myComplementarySet;

  public ComplementPackageSet(PackageSet set) {
    myComplementarySet = set;
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    return !myComplementarySet.contains(file, holder);
  }

  public PackageSet createCopy() {
    return new ComplementPackageSet(myComplementarySet.createCopy());
  }

  public String getText() {
    StringBuffer buf = new StringBuffer();
    boolean needParen = myComplementarySet.getNodePriority() > getNodePriority();
    buf.append('!');
    if (needParen) buf.append('(');
    buf.append(myComplementarySet.getText());
    if (needParen) buf.append(')');
    return buf.toString();
  }

  public int getNodePriority() {
    return 1;
  }
}
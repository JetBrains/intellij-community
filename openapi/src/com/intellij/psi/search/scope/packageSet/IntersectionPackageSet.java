/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.psi.PsiFile;

public class IntersectionPackageSet implements PackageSet {
  private PackageSet myFirstSet;
  private PackageSet mySecondSet;

  public IntersectionPackageSet(PackageSet firstSet, PackageSet secondSet) {
    myFirstSet = firstSet;
    mySecondSet = secondSet;
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    return myFirstSet.contains(file, holder) && mySecondSet.contains(file, holder);
  }

  public PackageSet createCopy() {
    return new IntersectionPackageSet(myFirstSet.createCopy(), mySecondSet.createCopy());
  }

  public int getNodePriority() {
    return 2;
  }

  public String getText() {
    StringBuffer buf = new StringBuffer();
    boolean needParen = myFirstSet.getNodePriority() > getNodePriority();
    if (needParen) buf.append('(');
    buf.append(myFirstSet.getText());
    if (needParen) buf.append(')');
    buf.append(" && ");
    needParen = mySecondSet.getNodePriority() > getNodePriority();
    if (needParen) buf.append('(');
    buf.append(mySecondSet.getText());
    if (needParen) buf.append(')');

    return buf.toString();
  }
}
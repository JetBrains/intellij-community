/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.packageDependencies.ui;

import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

public class GeneralGroupNode extends PackageDependenciesNode {
  private final String myName;
  private final Icon myOpenIcon;
  private final Icon myClosedIcon;

  public GeneralGroupNode(String name, Icon openIcon, Icon closedIcon) {
    myName = name;
    myOpenIcon = openIcon;
    myClosedIcon = closedIcon;
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      child.fillFiles(set, true);
    }
  }

  public String toString() {
    return myName;
  }

  public int getWeight() {
    return 2;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (!(o instanceof GeneralGroupNode)) return false;
    return myName.equals(((GeneralGroupNode)o).myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public Icon getOpenIcon() {
    return myOpenIcon;
  }

  public Icon getClosedIcon() {
    return myClosedIcon;
  }
}

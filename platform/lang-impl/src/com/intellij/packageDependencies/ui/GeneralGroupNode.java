// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

public final class GeneralGroupNode extends PackageDependenciesNode {
  private final String myName;
  private final Icon myIcon;

  public GeneralGroupNode(String name, Icon icon, Project project) {
    super(project);
    myName = name;
    myIcon = icon;
  }

  @Override
  public void fillFiles(Set<? super PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      child.fillFiles(set, true);
    }
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public int getWeight() {
    return 6;
  }

  @Override
  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (!(o instanceof GeneralGroupNode)) return false;
    return myName.equals(((GeneralGroupNode)o).myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }
}

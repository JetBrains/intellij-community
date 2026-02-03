// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

public final class LibraryNode extends PackageDependenciesNode {

  private final OrderEntry myLibraryOrJdk;

  public LibraryNode(OrderEntry libraryOrJdk, Project project) {
    super(project);
    myLibraryOrJdk = libraryOrJdk;
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
    return myLibraryOrJdk.getPresentableName();
  }

  @Override
  public int getWeight() {
    return 2;
  }

  @Override
  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof LibraryNode libraryNode)) return false;

    if (!myLibraryOrJdk.equals(libraryNode.myLibraryOrJdk)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myLibraryOrJdk.hashCode();
  }

  @Override
  public Icon getIcon() {
    return myLibraryOrJdk instanceof JdkOrderEntry ? AllIcons.Nodes.PpJdk : AllIcons.Nodes.PpLibFolder;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.packageDependencies.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

public class LibraryNode extends PackageDependenciesNode {

  private final OrderEntry myLibraryOrJdk;

  public LibraryNode(OrderEntry libraryOrJdk, Project project) {
    super(project);
    myLibraryOrJdk = libraryOrJdk;
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
    return myLibraryOrJdk.getPresentableName();
  }

  public int getWeight() {
    return 2;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof LibraryNode)) return false;

    final LibraryNode libraryNode = (LibraryNode)o;

    if (!myLibraryOrJdk.equals(libraryNode.myLibraryOrJdk)) return false;

    return true;
  }

  public int hashCode() {
    return myLibraryOrJdk.hashCode();
  }

  public Icon getIcon() {
    return myLibraryOrJdk instanceof JdkOrderEntry ? AllIcons.Nodes.PpJdk : AllIcons.Nodes.PpLibFolder;
  }
}

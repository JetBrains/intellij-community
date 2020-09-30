// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

class ClassNameConvertor implements TreeStructureProvider {

  private final Project myProject;

  ClassNameConvertor(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();

    for (final AbstractTreeNode aChildren : children) {
      ProjectViewNode treeNode = (ProjectViewNode)aChildren;
      Object o = treeNode.getValue();
      if (o instanceof PsiFile && ((PsiFile)o).getVirtualFile().getExtension().equals("java")) {
        final String name = ((PsiFile)o).getName();
        ProjectViewNode viewNode = new ProjectViewNode<>(myProject, (PsiFile)o, settings) {
          @Override
          @NotNull
          public Collection<AbstractTreeNode<?>> getChildren() {
            return Collections.emptyList();
          }

          @Override
          public String toTestString(Queryable.PrintInfo printInfo) {
            return super.toTestString(printInfo) + " converted";
          }

          @Override
          public String getTestPresentation() {
            return name + " converted";
          }

          @Override
          public boolean contains(@NotNull VirtualFile file) {
            return false;
          }

          @Override
          public void update(@NotNull PresentationData presentation) {
          }
        };
        result.add(viewNode);
      }
      else {
        result.add(treeNode);
      }
    }
    return result;
  }
}

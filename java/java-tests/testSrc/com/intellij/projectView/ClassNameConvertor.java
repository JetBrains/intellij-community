/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  public ClassNameConvertor(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode> result = new ArrayList<>();

    for (final AbstractTreeNode aChildren : children) {
      ProjectViewNode treeNode = (ProjectViewNode)aChildren;
      Object o = treeNode.getValue();
      if (o instanceof PsiFile && ((PsiFile)o).getVirtualFile().getExtension().equals("java")) {
        final String name = ((PsiFile)o).getName();
        ProjectViewNode viewNode = new ProjectViewNode<PsiFile>(myProject, (PsiFile)o, settings) {
          @Override
          @NotNull
          public Collection<AbstractTreeNode> getChildren() {
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
          public void update(PresentationData presentation) {
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

  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}

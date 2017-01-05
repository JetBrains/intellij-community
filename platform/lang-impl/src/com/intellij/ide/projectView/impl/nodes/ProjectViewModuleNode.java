/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProjectViewModuleNode extends AbstractModuleNode {

  public ProjectViewModuleNode(Project project, Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    Module module = getValue();
    if (module == null || module.isDisposed()) {  // module has been disposed
      return Collections.emptyList();
    }

    final List<VirtualFile> contentRoots = ProjectViewDirectoryHelper.getInstance(myProject).getTopLevelModuleRoots(module, getSettings());
    final List<AbstractTreeNode> children = new ArrayList<>(contentRoots.size());
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    for (final VirtualFile contentRoot : contentRoots) {
      if (contentRoot.isDirectory()) {
        PsiDirectory directory = psiManager.findDirectory(contentRoot);
        if (directory != null) {
          children.add(new PsiDirectoryNode(getProject(), directory, getSettings()));
        }
      }
      else {
        PsiFile file = psiManager.findFile(contentRoot);
        if (file != null) {
          children.add(new PsiFileNode(getProject(), file, getSettings()));
        }
      }
    }
    return children;
  }

  @Override
  public int getWeight() {
    return 10;
  }

  @Override
  public int getTypeSortWeight(final boolean sortByType) {
    return 2;
  }
}

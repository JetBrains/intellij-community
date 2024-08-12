// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProjectViewModuleGroupNode extends ModuleGroupNode {
  public ProjectViewModuleGroupNode(final Project project, @NotNull ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  protected @NotNull AbstractTreeNode createModuleNode(@NotNull Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length == 1) {
      final PsiDirectory psi = PsiManager.getInstance(myProject).findDirectory(roots[0]);
      if (psi != null) {
        return new PsiDirectoryNode(myProject, psi, getSettings());
      }
    }

    return new ProjectViewModuleNode(getProject(), module, getSettings());
  }

  @Override
  protected @NotNull ModuleGroupNode createModuleGroupNode(@NotNull ModuleGroup moduleGroup) {
    return new ProjectViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }


  @Override
  protected @NotNull List<Module> getModulesByFile(@NotNull VirtualFile file) {
    return ContainerUtil.createMaybeSingletonList(ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file, false));
  }
}

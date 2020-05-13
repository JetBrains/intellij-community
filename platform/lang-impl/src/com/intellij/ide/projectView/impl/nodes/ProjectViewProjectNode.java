// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ProjectViewProjectNode extends AbstractProjectNode {

  public ProjectViewProjectNode(@NotNull Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @Override
  public boolean canRepresent(Object element) {
    Project project = getValue();
    return project == element || project != null && element instanceof VirtualFile && element.equals(project.getBaseDir());
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    if (myProject.isDisposed()) return Collections.emptyList();
    List<VirtualFile> topLevelContentRoots = ProjectViewDirectoryHelper.getInstance(myProject).getTopLevelRoots();

    Set<ModuleDescription> modules = new LinkedHashSet<>(topLevelContentRoots.size());
    for (VirtualFile root : topLevelContentRoots) {
      final Module module = ModuleUtilCore.findModuleForFile(root, myProject);
      if (module != null) {
        modules.add(new LoadedModuleDescriptionImpl(module));
      }
      else {
        String unloadedModuleName = ProjectRootsUtil.findUnloadedModuleByContentRoot(root, myProject);
        if (unloadedModuleName != null) {
          ContainerUtil.addIfNotNull(modules, ModuleManager.getInstance(myProject).getUnloadedModuleDescription(unloadedModuleName));
        }
      }
    }

    final PsiManager psiManager = PsiManager.getInstance(getProject());

    List<AbstractTreeNode<?>> nodes = new ArrayList<>(modulesAndGroups(modules));

    final VirtualFile baseDir = getProject().getBaseDir();
    if (baseDir == null) return nodes;

    final VirtualFile[] files = baseDir.getChildren();
    for (VirtualFile file : files) {
      if (!file.isDirectory()) {
        if (ProjectFileIndex.SERVICE.getInstance(getProject()).getModuleForFile(file, false) == null) {
          PsiFile psiFile = psiManager.findFile(file);
          if (psiFile != null) {
            nodes.add(new PsiFileNode(getProject(), psiFile, getSettings()));
          }
        }
      }
    }

    if (getSettings().isShowLibraryContents()) {
      nodes.add(new ExternalLibrariesNode(getProject(), getSettings()));
    }
    return nodes;
  }

  @NotNull
  @Override
  protected AbstractTreeNode createModuleGroup(@NotNull final Module module) {
    List<VirtualFile> roots = ProjectViewDirectoryHelper.getInstance(myProject).getTopLevelModuleRoots(module, getSettings());
    if (roots.size() == 1) {
      final PsiDirectory psi = PsiManager.getInstance(myProject).findDirectory(roots.get(0));
      if (psi != null) {
        return new PsiDirectoryNode(myProject, psi, getSettings());
      }
    }

    return new ProjectViewModuleNode(getProject(), module, getSettings());
  }

  @Override
  protected AbstractTreeNode createUnloadedModuleNode(@NotNull UnloadedModuleDescription moduleDescription) {
    List<VirtualFile> roots = ProjectViewDirectoryHelper.getInstance(myProject).getTopLevelUnloadedModuleRoots(moduleDescription, getSettings());
    if (roots.size() == 1) {
      final PsiDirectory psi = PsiManager.getInstance(myProject).findDirectory(roots.get(0));
      if (psi != null) {
        return new PsiDirectoryNode(myProject, psi, getSettings());
      }
    }

    return new ProjectViewUnloadedModuleNode(getProject(), moduleDescription, getSettings());
  }

  @NotNull
  @Override
  protected AbstractTreeNode createModuleGroupNode(@NotNull final ModuleGroup moduleGroup) {
    return new ProjectViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }
}

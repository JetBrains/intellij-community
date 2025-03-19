// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.LocalFileSystem;
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
  public boolean isAlwaysShowPlus() {
    return true;
  }

  @Override
  public boolean canRepresent(Object element) {
    Project project = getValue();
    return project == element || project != null && element instanceof VirtualFile && element.equals(project.getBaseDir());
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    Project project = myProject;
    if (project == null || project.isDisposed() || project.isDefault()) {
      return Collections.emptyList();
    }

    List<VirtualFile> topLevelContentRoots = ProjectViewDirectoryHelper.getInstance(project).getTopLevelRoots();

    Set<ModuleDescription> modules = new LinkedHashSet<>(topLevelContentRoots.size());
    for (VirtualFile root : topLevelContentRoots) {
      Module module = ModuleUtilCore.findModuleForFile(root, project);
      if (module != null) {
        modules.add(new LoadedModuleDescriptionImpl(module));
      }
      else {
        String unloadedModuleName = ProjectRootsUtil.findUnloadedModuleByContentRoot(root, project);
        if (unloadedModuleName != null) {
          ContainerUtil.addIfNotNull(modules, ModuleManager.getInstance(project).getUnloadedModuleDescription(unloadedModuleName));
        }
      }
    }


    List<AbstractTreeNode<?>> nodes = new ArrayList<>(modulesAndGroups(modules));

    String baseDirPath = project.getBasePath();
    VirtualFile baseDir = baseDirPath == null ? null : LocalFileSystem.getInstance().findFileByPath(baseDirPath);
    if (baseDir != null) {
      PsiManager psiManager = PsiManager.getInstance(project);
      VirtualFile[] files = baseDir.getChildren();
      ProjectFileIndex projectFileIndex = null;
      for (VirtualFile file : files) {
        if (!file.isDirectory()) {
          if (projectFileIndex == null) {
            projectFileIndex = ProjectFileIndex.getInstance(getProject());
          }
          if (projectFileIndex.getModuleForFile(file, false) == null) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {
              nodes.add(new PsiFileNode(getProject(), psiFile, getSettings()));
            }
          }
        }
      }
    }

    if (getSettings().isShowLibraryContents()) {
      nodes.add(new ExternalLibrariesNode(project, getSettings()));
    }
    return nodes;
  }

  @Override
  protected @NotNull AbstractTreeNode<?> createModuleGroup(final @NotNull Module module) {
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
  protected AbstractTreeNode<?> createUnloadedModuleNode(@NotNull UnloadedModuleDescription moduleDescription) {
    List<VirtualFile> roots = ProjectViewDirectoryHelper.getInstance(myProject).getTopLevelUnloadedModuleRoots(moduleDescription, getSettings());
    if (roots.size() == 1) {
      final PsiDirectory psi = PsiManager.getInstance(myProject).findDirectory(roots.get(0));
      if (psi != null) {
        return new PsiDirectoryNode(myProject, psi, getSettings());
      }
    }

    return new ProjectViewUnloadedModuleNode(getProject(), moduleDescription, getSettings());
  }

  @Override
  protected @NotNull AbstractTreeNode createModuleGroupNode(final @NotNull ModuleGroup moduleGroup) {
    return new ProjectViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }
}

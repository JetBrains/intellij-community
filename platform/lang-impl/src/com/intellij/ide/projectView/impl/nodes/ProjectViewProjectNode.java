/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.scratch.ScratchProjectViewPane;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.*;
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ProjectViewProjectNode extends AbstractProjectNode {

  public ProjectViewProjectNode(Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
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

    List<AbstractTreeNode> nodes = new ArrayList<>(modulesAndGroups(modules));

    final VirtualFile baseDir = getProject().getBaseDir();
    if (baseDir == null) return nodes;

    final VirtualFile[] files = baseDir.getChildren();
    for (VirtualFile file : files) {
      if (!file.isDirectory()) {
        if (ProjectFileIndex.SERVICE.getInstance(getProject()).getModuleForFile(file, false) == null) {
          nodes.add(new PsiFileNode(getProject(), psiManager.findFile(file), getSettings()));
        }
      }
    }

    if (getSettings().isShowLibraryContents()) {
      nodes.add(new ExternalLibrariesNode(getProject(), getSettings()));
    }
    if (ScratchProjectViewPane.isScratchesMergedIntoProjectTab()) {
      nodes.add(ScratchProjectViewPane.createRootNode(getProject(), getSettings()));
    }

    return nodes;
  }

  @Override
  protected AbstractTreeNode createModuleGroup(final Module module) {
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
  protected AbstractTreeNode createUnloadedModuleNode(UnloadedModuleDescription moduleDescription) {
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
  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup) {
    return new ProjectViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }
}

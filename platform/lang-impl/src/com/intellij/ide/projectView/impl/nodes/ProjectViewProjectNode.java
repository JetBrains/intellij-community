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
import com.intellij.ide.scratch.ScratchProjectViewPane;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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

    Set<Module> modules = new LinkedHashSet<>(topLevelContentRoots.size());

    for (VirtualFile root : topLevelContentRoots) {
      final Module module = ModuleUtil.findModuleForFile(root, myProject);
      if (module != null) { // Some people exclude module's content roots...
        modules.add(module);
      }
    }

    ArrayList<AbstractTreeNode> nodes = new ArrayList<>();
    final PsiManager psiManager = PsiManager.getInstance(getProject());

    /*
    for (VirtualFile root : reduceRoots(topLevelContentRoots)) {
      nodes.add(new PsiDirectoryNode(getProject(), psiManager.findDirectory(root), getSettings()));
    }
    */

    nodes.addAll(modulesAndGroups(modules.toArray(new Module[modules.size()])));

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
    if (Registry.is("ide.scratch.in.project.view")) {
      nodes.add(ScratchProjectViewPane.createRootNode(getProject(), getSettings()));
    }

    return nodes;
  }

  private static List<VirtualFile> reduceRoots(List<VirtualFile> roots) {
    if (roots.isEmpty()) return Collections.emptyList();

    String userHome;
    try {
      userHome = FileUtil.toSystemIndependentName(new File(SystemProperties.getUserHome()).getCanonicalPath());
    }
    catch (IOException e) {
      userHome = null;
    }

    Collections.sort(roots, (o1, o2) -> o1.getPath().compareTo(o2.getPath()));

    Iterator<VirtualFile> it = roots.iterator();
    VirtualFile current = it.next();

    List<VirtualFile> reducedRoots = new ArrayList<>();
    while (it.hasNext()) {
      VirtualFile next = it.next();
      VirtualFile common = VfsUtilCore.getCommonAncestor(current, next);

      if (common == null || common.getParent() == null || Comparing.equal(common.getPath(), userHome)) {
        reducedRoots.add(current);
        current = next;
      }
      else {
        current = common;
      }
    }

    reducedRoots.add(current);
    return reducedRoots;
  }

  @Override
  protected AbstractTreeNode createModuleGroup(final Module module)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
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
  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return new ProjectViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }
}

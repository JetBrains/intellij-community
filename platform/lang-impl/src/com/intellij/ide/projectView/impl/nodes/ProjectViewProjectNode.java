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

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
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

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    List<VirtualFile> topLevelContentRoots = ProjectViewDirectoryHelper.getInstance(myProject).getTopLevelRoots();

    Set<Module> modules = new LinkedHashSet<Module>(topLevelContentRoots.size());

    for (VirtualFile root : topLevelContentRoots) {
      modules.add(ModuleUtil.findModuleForFile(root, myProject));
    }

    ArrayList<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
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
      if (ModuleUtil.findModuleForFile(file, getProject()) == null) {
        if (!file.isDirectory()) {
          nodes.add(new PsiFileNode(getProject(), psiManager.findFile(file), getSettings()));
        }
      }
    }

    if (getSettings().isShowLibraryContents()) {
      nodes.add(new ExternalLibrariesNode(getProject(), getSettings()));
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

    Collections.sort(roots, new java.util.Comparator<VirtualFile>() {
      public int compare(VirtualFile o1, VirtualFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });

    Iterator<VirtualFile> it = roots.iterator();
    VirtualFile current = it.next();

    List<VirtualFile> reducedRoots = new ArrayList<VirtualFile>();
    while (it.hasNext()) {
      VirtualFile next = it.next();
      VirtualFile common = VfsUtil.getCommonAncestor(current, next);

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

  protected AbstractTreeNode createModuleGroup(final Module module)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length == 1) {
      final PsiDirectory psi = PsiManager.getInstance(myProject).findDirectory(roots[0]);
      if (psi != null) {
        return createTreeNode(PsiDirectoryNode.class, myProject, psi, getSettings());
      }
    }

    return createTreeNode(ProjectViewModuleNode.class, getProject(), module, getSettings());
  }

  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return createTreeNode(ProjectViewModuleGroupNode.class, getProject(), moduleGroup, getSettings());
  }
}

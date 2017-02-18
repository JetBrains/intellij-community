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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class PackageViewProjectNode extends AbstractProjectNode {
  public PackageViewProjectNode(Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    if (getSettings().isShowModules()) {
      final List<Module> allModules = new ArrayList<>(Arrays.asList(ModuleManager.getInstance(getProject()).getModules()));
      for (Iterator<Module> it = allModules.iterator(); it.hasNext();) {
        final Module module = it.next();
        final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
        if (sourceRoots.length == 0) {
          // do not show modules with no source roots configured
          it.remove();
        }
      }
      return modulesAndGroups(allModules.toArray(new Module[allModules.size()]));
    }
    else {
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final List<AbstractTreeNode> children = new ArrayList<>();
      final Set<PsiPackage> topLevelPackages = new HashSet<>();

      for (final VirtualFile root : projectRootManager.getContentSourceRoots()) {
        final PsiDirectory directory = psiManager.findDirectory(root);
        if (directory == null) {
          continue;
        }
        final PsiPackage directoryPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (directoryPackage == null || PackageUtil.isPackageDefault(directoryPackage)) {
          // add subpackages
          final PsiDirectory[] subdirectories = directory.getSubdirectories();
          for (PsiDirectory subdirectory : subdirectories) {
            final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subdirectory);
            if (aPackage != null && !PackageUtil.isPackageDefault(aPackage)) {
              topLevelPackages.add(aPackage);
            }
          }
          // add non-dir items
          children.addAll(ProjectViewDirectoryHelper.getInstance(myProject).getDirectoryChildren(directory, getSettings(), false));
        }
        else {
          // this is the case when a source root has pakage prefix assigned
          topLevelPackages.add(directoryPackage);
        }
      }

      for (final PsiPackage psiPackage : topLevelPackages) {
        PackageUtil.addPackageAsChild(children, psiPackage, null, getSettings(), false);
      }

      if (getSettings().isShowLibraryContents()) {
        children.add(new PackageViewLibrariesNode(getProject(), null, getSettings()));
      }

      return children;
    }


  }

  @Override
  protected AbstractTreeNode createModuleGroup(final Module module) throws
                                                                    InvocationTargetException,
                                                                    NoSuchMethodException, InstantiationException, IllegalAccessException {
    return new PackageViewModuleNode(getProject(), module, getSettings());
  }

  @Override
  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup)
    throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return new PackageViewModuleGroupNode(getProject(),  moduleGroup, getSettings());
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    return true;
  }
}

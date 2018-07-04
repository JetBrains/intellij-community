// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleDescription;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PackageViewProjectNode extends AbstractProjectNode {
  public PackageViewProjectNode(Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @Override
  public boolean canRepresent(Object element) {
    Project project = getValue();
    if (project == element) return true;
    if (element instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)element;
      element = directory.getVirtualFile();
    }
    if (element instanceof VirtualFile) {
      ProjectRootManager manager = project == null || project.isDisposed() ? null : ProjectRootManager.getInstance(project);
      if (manager != null) {
        for (VirtualFile root : manager.getContentSourceRoots()) {
          if (element.equals(root)) return true;
        }
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    if (getSettings().isShowModules()) {
      List<ModuleDescription> modulesWithSourceRoots = new ArrayList<>();
      for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
        if (ModuleRootManager.getInstance(module).getSourceRoots().length > 0) {
          modulesWithSourceRoots.add(new LoadedModuleDescriptionImpl(module));
        }
      }
      return modulesAndGroups(modulesWithSourceRoots);
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
  protected AbstractTreeNode createModuleGroup(final Module module) {
    return new PackageViewModuleNode(getProject(), module, getSettings());
  }

  @Override
  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup) {
    return new PackageViewModuleGroupNode(getProject(),  moduleGroup, getSettings());
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    return true;
  }
}

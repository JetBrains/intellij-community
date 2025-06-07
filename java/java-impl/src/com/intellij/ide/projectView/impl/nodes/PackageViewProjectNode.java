// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static com.intellij.ide.projectView.impl.nodes.ImplKt.moduleDescriptions;

public class PackageViewProjectNode extends AbstractProjectNode {
  public PackageViewProjectNode(@NotNull Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @Override
  public boolean canRepresent(Object element) {
    Project project = getValue();
    if (project == element) return true;
    if (element instanceof PsiDirectory directory) {
      element = directory.getVirtualFile();
    }
    if (element instanceof VirtualFile) {
      ProjectRootManager manager = project == null || project.isDisposed() ? null : ProjectRootManager.getInstance(project);
      if (manager != null) {
        return ArrayUtil.contains(element, manager.getContentSourceRoots());
      }
    }
    return false;
  }

  @Override
  public @Unmodifiable @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    if (getSettings().isShowModules()) {
      return modulesAndGroups(moduleDescriptions(myProject));
    }
    else {
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final List<AbstractTreeNode<?>> children = new ArrayList<>();
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

      var nodeBuilder = new PackageNodeBuilder(null, false);
      for (final PsiPackage psiPackage : topLevelPackages) {
        nodeBuilder.addPackageAsChild(children, psiPackage, getSettings());
      }

      if (getSettings().isShowLibraryContents()) {
        children.add(new PackageViewLibrariesNode(getProject(), null, getSettings()));
      }

      return children;
    }


  }

  @Override
  protected @NotNull AbstractTreeNode createModuleGroup(final @NotNull Module module) {
    return new PackageViewModuleNode(getProject(), module, getSettings());
  }

  @Override
  protected @NotNull AbstractTreeNode createModuleGroupNode(final @NotNull ModuleGroup moduleGroup) {
    return new PackageViewModuleGroupNode(getProject(),  moduleGroup, getSettings());
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    return true;
  }
}

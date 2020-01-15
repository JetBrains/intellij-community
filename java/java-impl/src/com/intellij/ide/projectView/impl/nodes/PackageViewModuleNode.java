// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PackageViewModuleNode extends AbstractModuleNode{
  public PackageViewModuleNode(Project project, @NotNull Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    return AbstractTreeUi.calculateYieldingToWriteAction(() -> {
      Module module = getValue();
      if (module == null || module.isDisposed()) return Collections.emptyList();
      List<VirtualFile> roots = Arrays.asList(ModuleRootManager.getInstance(module).getSourceRoots());
      final Collection<AbstractTreeNode<?>> result = PackageUtil.createPackageViewChildrenOnFiles(roots, myProject, getSettings(), module, false);
      if (getSettings().isShowLibraryContents()) {
        result.add(new PackageViewLibrariesNode(getProject(), module, getSettings()));
      }
      return result;
    });
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Module module = getValue();
    return module != null && !module.isDisposed() &&
           (ModuleUtilCore.moduleContainsFile(module, file, false) || ModuleUtilCore.moduleContainsFile(module, file, true));
  }

  @Override
  public boolean validate() {
    return getValue() != null;
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    return true;
  }
}

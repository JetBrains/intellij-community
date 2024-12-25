// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public class PackageViewModuleGroupNode extends ModuleGroupNode {

  public PackageViewModuleGroupNode(final Project project, @NotNull ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  protected @NotNull AbstractTreeNode createModuleNode(@NotNull Module module) {
    return new PackageViewModuleNode(module.getProject(), module, getSettings());
  }

  @Override
  protected @NotNull ModuleGroupNode createModuleGroupNode(@NotNull ModuleGroup moduleGroup) {
    return new PackageViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }

  @Override
  protected @Unmodifiable @NotNull List<Module> getModulesByFile(@NotNull VirtualFile file) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    Module module = fileIndex.getModuleForFile(file, false);
    if (module != null) {
      return Collections.singletonList(module);
    }
    List<OrderEntry> entriesForFile = fileIndex.getOrderEntriesForFile(file);
    return ContainerUtil.map(entriesForFile, OrderEntry::getOwnerModule);
  }
}

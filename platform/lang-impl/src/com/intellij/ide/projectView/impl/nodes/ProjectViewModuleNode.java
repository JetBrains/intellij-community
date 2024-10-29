// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProjectViewModuleNode extends AbstractModuleNode {

  public ProjectViewModuleNode(Project project, @NotNull Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    Module module = getValue();
    if (module == null || module.isDisposed()) {  // module has been disposed
      return Collections.emptyList();
    }

    final List<VirtualFile> contentRoots = ProjectViewDirectoryHelper.getInstance(myProject).getTopLevelModuleRoots(module, getSettings());
    return ProjectViewDirectoryHelper.getInstance(myProject).createFileAndDirectoryNodes(contentRoots, getSettings());
  }

  @Override
  public int getWeight() {
    return 10;
  }

  @Override
  public int getTypeSortWeight(final boolean sortByType) {
    return 2;
  }
}

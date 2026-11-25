// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class SliceTreeStructure extends AbstractTreeStructureBase {
  private final SliceRootNode myRoot;

  public SliceTreeStructure(@NotNull Project project, @NotNull SliceRootNode rootNode) {
    super(project);
    myRoot = rootNode;
  }

  @Override
  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull SliceRootNode getRootElement() {
    return myRoot;
  }

  public @Nullable SliceTreeStructure copyForAnalysis() {
    return ReadAction.computeBlocking(() -> {
      SliceUsage usage = myRoot.getRootUsage();
      if (usage.getElement() == null) return null;

      SliceRootNode root = new SliceRootNode(myProject, new DuplicateMap(), usage.copy());
      return new SliceTreeStructure(myProject, root);
    });
  }

  @Override
  public void commit() {

  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public boolean isToBuildChildrenInBackground(final @NotNull Object element) {
    return true;//!ApplicationManager.getApplication().isUnitTestMode();
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class SliceRootNode extends SliceNode {
  private final SliceUsage myRootUsage;

  public SliceRootNode(@NotNull Project project, @NotNull DuplicateMap targetEqualUsages, @NotNull SliceUsage rootUsage) {
    super(project,
          LanguageSlicing.getProvider(rootUsage.getElement().getContainingFile()).
            createRootUsage(rootUsage.getElement().getContainingFile(), rootUsage.params),
          targetEqualUsages);
    myRootUsage = rootUsage;
  }

  private void switchToAllLeavesTogether(SliceUsage rootUsage) {
    SliceNode node = new SliceNode(getProject(), rootUsage, targetEqualUsages);
    myCachedChildren = Collections.singletonList(node);
  }

  @NotNull
  @Override
  public SliceRootNode copy() {
    SliceUsage newUsage = getValue().copy();
    SliceRootNode newNode = new SliceRootNode(getProject(), new DuplicateMap(), newUsage);
    newNode.dupNodeCalculated = dupNodeCalculated;
    newNode.duplicate = duplicate;
    return newNode;
  }

  @Override
  @NotNull
  public Collection<SliceNode> getChildren() {
    if (myCachedChildren == null) {
      switchToAllLeavesTogether(myRootUsage);
    }
    return myCachedChildren;
  }


  @Override
  public void customizeCellRenderer(@NotNull SliceUsageCellRendererBase renderer,
                                    @NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
  }

  @NotNull
  public SliceUsage getRootUsage() {
    return myRootUsage;
  }

  public void setChildren(@NotNull List<? extends SliceNode> children) {
    myCachedChildren = new ArrayList<>(children);
  }
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceRootNode extends SliceNode {
  private final SliceUsage myRootUsage;

  public SliceRootNode(@NotNull Project project, @NotNull DuplicateMap targetEqualUsages, final SliceUsage rootUsage) {
    super(project,
          LanguageSlicing.getProvider(rootUsage.getElement().getContainingFile()).
            createRootUsage(rootUsage.getElement().getContainingFile(), rootUsage.params),
          targetEqualUsages);
    myRootUsage = rootUsage;
  }

  void switchToAllLeavesTogether(SliceUsage rootUsage) {
    SliceNode node = new SliceNode(getProject(), rootUsage, targetEqualUsages);
    myCachedChildren = Collections.singletonList(node);
  }

  @NotNull
  @Override
  SliceRootNode copy() {
    SliceUsage newUsage = getValue().copy();
    SliceRootNode newNode = new SliceRootNode(getProject(), new DuplicateMap(), newUsage);
    newNode.dupNodeCalculated = dupNodeCalculated;
    newNode.duplicate = duplicate;
    return newNode;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    if (myCachedChildren == null) {
      switchToAllLeavesTogether(myRootUsage);
    }
    return myCachedChildren;
  }

  @NotNull
  @Override
  public List<? extends AbstractTreeNode> getChildrenUnderProgress(@NotNull ProgressIndicator progress) {
    return (List<? extends AbstractTreeNode>)getChildren();
  }

  @Override
  protected boolean shouldUpdateData() {
    return super.shouldUpdateData();
  }

  @Override
  protected void update(PresentationData presentation) {
    if (presentation != null) {
      presentation.setChanged(presentation.isChanged() || changed);
      changed = false;
    }
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

  public SliceUsage getRootUsage() {
    return myRootUsage;
  }
}

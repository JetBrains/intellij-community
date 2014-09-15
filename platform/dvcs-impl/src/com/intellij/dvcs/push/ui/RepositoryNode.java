/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.OutgoingResult;
import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class RepositoryNode extends CheckedTreeNode implements EditableTreeNode, Comparable<RepositoryNode> {
  @NotNull private final RepositoryWithBranchPanel myRepositoryPanel;
  private Future<AtomicReference<OutgoingResult>> myFuture;

  public RepositoryNode(@NotNull RepositoryWithBranchPanel repositoryPanel) {
    super(repositoryPanel);
    myRepositoryPanel = repositoryPanel;
  }

  public boolean isCheckboxVisible() {
    return true;
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    String repositoryPath = myRepositoryPanel.getRepositoryName();
    renderer.append(repositoryPath, SimpleTextAttributes.GRAY_ATTRIBUTES);
    renderer.appendFixedTextFragmentWidth(120);
    renderer.append(myRepositoryPanel.getSourceName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.append(myRepositoryPanel.getArrow(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    PushTargetPanel pushTargetPanel = myRepositoryPanel.getTargetPanel();
    pushTargetPanel.render(renderer);
    Insets insets = BorderFactory.createEmptyBorder().getBorderInsets(pushTargetPanel);
    renderer.setBorder(new EmptyBorder(insets));
  }

  @Override
  public Object getUserObject() {
    return myRepositoryPanel;
  }

  @Override
  public void fireOnChange() {
    myRepositoryPanel.fireOnChange();
  }

  @Override
  public void fireOnCancel() {
    myRepositoryPanel.fireOnCancel();
  }

  @Override
  public void fireOnSelectionChange(boolean isSelected) {
    myRepositoryPanel.fireOnSelectionChange(isSelected);
  }

  @Override
  public void stopLoading() {
    if (myFuture != null && !myFuture.isDone()) {
      myFuture.cancel(true);
    }
  }

  @Override
  public void startLoading(@NotNull Future<AtomicReference<OutgoingResult>> future) {
    myFuture = future;
  }

  public int compareTo(@NotNull RepositoryNode repositoryNode) {
    String name = myRepositoryPanel.getRepositoryName();
    RepositoryWithBranchPanel panel = (RepositoryWithBranchPanel)repositoryNode.getUserObject();
    return name.compareTo(panel.getRepositoryName());
  }
}

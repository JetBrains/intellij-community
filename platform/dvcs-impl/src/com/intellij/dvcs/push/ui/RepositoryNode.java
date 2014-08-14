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

import com.intellij.dvcs.push.RepositoryNodeListener;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class RepositoryNode extends CheckedTreeNode implements EditableTreeNode {
  protected final static String ENTER_REMOTE = "Enter Remote";

  @NotNull private final List<RepositoryNodeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull private final RepositoryWithBranchPanel myRepositoryPanel;

  private ProgressIndicator myCurrentIndicator;

  public RepositoryNode(@NotNull RepositoryWithBranchPanel repositoryPanel) {
    super(repositoryPanel);
    myRepositoryPanel = repositoryPanel;
  }

  public boolean isCheckboxVisible() {
    return true;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    String repositoryPath = myRepositoryPanel.getRepositoryName();
    renderer.append(repositoryPath, SimpleTextAttributes.GRAY_ATTRIBUTES);
    renderer.appendFixedTextFragmentWidth(120);
    renderer.append(myRepositoryPanel.getSourceName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.append(myRepositoryPanel.getArrow(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    EditorTextField textField = myRepositoryPanel.getRemoteTextFiled();
    String targetName = myRepositoryPanel.getRemoteTargetName();
    if (StringUtil.isEmptyOrSpaces(targetName)) {
      renderer.append(ENTER_REMOTE, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, textField);
    }
    else {
      renderer.append(targetName, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES, textField);
    }
    Insets insets = BorderFactory.createEmptyBorder().getBorderInsets(textField);
    renderer.setBorder(new EmptyBorder(insets));
  }

  @Override
  @NotNull
  public String getValue() {
    return myRepositoryPanel.getRemoteTargetName();
  }

  @Override
  public void addRepoNodeListener(@NotNull RepositoryNodeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void fireOnChange(@NotNull String newValue) {
    for (RepositoryNodeListener listener : myListeners) {
      listener.onTargetChanged(newValue);
    }
  }

  @Override
  public void fireOnSelectionChange(boolean isSelected) {
    for (RepositoryNodeListener listener : myListeners) {
      listener.onSelectionChanged(isSelected);
    }
  }

  @Override
  public void stopLoading() {
    if (myCurrentIndicator != null && myCurrentIndicator.isRunning()) {
      myCurrentIndicator.cancel();
    }
  }

  @Override
  public ProgressIndicator startLoading() {
    return myCurrentIndicator = new EmptyProgressIndicator();
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.OutgoingResult;
import com.intellij.dvcs.push.PushTarget;
import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RepositoryNode extends CheckedTreeNode implements EditableTreeNode, Comparable<RepositoryNode> {

  @NotNull protected final AtomicBoolean myLoading = new AtomicBoolean();
  @NotNull private final CheckBoxModel myCheckBoxModel;

  @NotNull private final RepositoryWithBranchPanel myRepositoryPanel;
  @Nullable private Future<AtomicReference<OutgoingResult>> myFuture;

  public RepositoryNode(@NotNull RepositoryWithBranchPanel repositoryPanel, @NotNull CheckBoxModel model, boolean enabled) {
    super(repositoryPanel);
    myCheckBoxModel = model;
    setChecked(false);
    setEnabled(enabled);
    myRepositoryPanel = repositoryPanel;
  }

  @Override
  public boolean isChecked() {
    return myCheckBoxModel.isChecked();
  }

  @Override
  public void setChecked(boolean checked) {
    myCheckBoxModel.setChecked(checked);
  }

  public boolean isCheckboxVisible() {
    return true;
  }

  public void forceUpdateUiModelWithTypedText(@NotNull String forceText) {
    myRepositoryPanel.getTargetPanel().forceUpdateEditableUiModel(forceText);
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    render(renderer, null);
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer, @Nullable @Nls String syncEditingText) {
    int repoFixedWidth = 120;
    SimpleTextAttributes repositoryDetailsTextAttributes = PushLogTreeUtil
      .addTransparencyIfNeeded(renderer, SimpleTextAttributes.REGULAR_ATTRIBUTES, isChecked());

    renderer.append(getRepoName(renderer, repoFixedWidth), repositoryDetailsTextAttributes);
    renderer.appendTextPadding(repoFixedWidth);
    renderer.append(myRepositoryPanel.getSourceName(), repositoryDetailsTextAttributes);
    renderer.append(myRepositoryPanel.getArrow(), repositoryDetailsTextAttributes);
    PushTargetPanel pushTargetPanel = myRepositoryPanel.getTargetPanel();
    pushTargetPanel.render(renderer, renderer.getTree().isPathSelected(TreeUtil.getPathFromRoot(this)), isChecked(), syncEditingText);
  }

  @Nls
  @NotNull
  private String getRepoName(@NotNull ColoredTreeCellRenderer renderer, int maxWidth) {
    String name = getRepositoryName();
    return GraphicsUtil.stringWidth(name, renderer.getFont()) > maxWidth - UIUtil.DEFAULT_HGAP ? name + "  " : name;
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
  public void cancelLoading() {
    if (myFuture != null && !myFuture.isDone()) {
      myFuture.cancel(true);
    }
  }

  @Override
  public void startLoading(@NotNull final JTree tree, @NotNull Future<AtomicReference<OutgoingResult>> future, boolean initial) {
    myFuture = future;
    myLoading.set(true);
  }

  @Override
  public boolean isEditableNow() {
    return myRepositoryPanel.isEditable();
  }

  @Override
  public int compareTo(@NotNull RepositoryNode repositoryNode) {
    String name = myRepositoryPanel.getRepositoryName();
    RepositoryWithBranchPanel panel = (RepositoryWithBranchPanel)repositoryNode.getUserObject();
    return name.compareTo(panel.getRepositoryName());
  }

  public void stopLoading() {
    myLoading.set(false);
  }

  public boolean isLoading() {
    return myLoading.get();
  }

  @Nls
  @NotNull
  String getRepositoryName() {
    return myRepositoryPanel.getRepositoryName();
  }

  @Override
  public String toString() {
    return getRepositoryName() + " " + getRepositoryPresentationDetails();
  }

  @NotNull
  protected String getRepositoryPresentationDetails() {
    PushTarget targetValue = myRepositoryPanel.getTargetPanel().getValue();
    return myRepositoryPanel.getSourceName() + (targetValue != null ? myRepositoryPanel.getArrow() + targetValue : "");
  }
}

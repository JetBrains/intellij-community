// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class SingleRepositoryNode extends RepositoryNode {

  private final @NotNull RepositoryWithBranchPanel myRepositoryPanel;

  public SingleRepositoryNode(@NotNull RepositoryWithBranchPanel repositoryPanel, @NotNull CheckBoxModel model) {
    super(repositoryPanel, model, true);
    myRepositoryPanel = repositoryPanel;
  }

  @Override
  public boolean isCheckboxVisible() {
    return false;
  }

  @Override
  public void setChecked(boolean checked) {
  }

  @Override
  public void fireOnSelectionChange(boolean isSelected) {
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(" ");
    renderer.append(myRepositoryPanel.getSourceName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.append(myRepositoryPanel.getArrow(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    PushTargetPanel pushTargetPanel = myRepositoryPanel.getTargetPanel();
    pushTargetPanel.render(renderer, renderer.getTree().isPathSelected(TreeUtil.getPathFromRoot(this)), true, null);
  }

  @Override
  public String toString() {
    return getRepositoryPresentationDetails();
  }
}

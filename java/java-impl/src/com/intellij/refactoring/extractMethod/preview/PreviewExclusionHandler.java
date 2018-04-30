// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.ide.actions.exclusion.ExclusionHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Pavel.Dolgov
 */
class PreviewExclusionHandler implements ExclusionHandler<DefaultMutableTreeNode> {
  private PreviewPanel myPanel;

  public PreviewExclusionHandler(PreviewPanel panel) {myPanel = panel;}

  @Override
  public boolean isNodeExclusionAvailable(@NotNull DefaultMutableTreeNode node) {
    return node instanceof DuplicateNode;
  }

  @Override
  public boolean isNodeExcluded(@NotNull DefaultMutableTreeNode node) {
    return node instanceof DuplicateNode && ((DuplicateNode)node).isExcluded();
  }

  @Override
  public void excludeNode(@NotNull DefaultMutableTreeNode node) {
    if (node instanceof DuplicateNode) {
      ((DuplicateNode)node).setExcluded(true);
    }
  }

  @Override
  public void includeNode(@NotNull DefaultMutableTreeNode node) {
    if (node instanceof DuplicateNode) {
      ((DuplicateNode)node).setExcluded(false);
    }
  }

  @Override
  public boolean isActionEnabled(boolean isExcludeAction) {
    return true;
  }

  @Override
  public void onDone(boolean isExcludeAction) {
    myPanel.onTreeUpdated();
  }
}

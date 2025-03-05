// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;


import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

@ApiStatus.Internal
public class TextWithLinkNode extends DefaultMutableTreeNode implements CustomRenderedTreeNode {

  protected @NotNull VcsLinkedTextComponent myLinkedText;

  public TextWithLinkNode(@NotNull VcsLinkedTextComponent linkedText) {
    myLinkedText = linkedText;
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append("   ");
    myLinkedText.setSelected(renderer.getTree().isPathSelected(TreeUtil.getPathFromRoot(this)));
    TreeNode parent = getParent();
    if (parent instanceof RepositoryNode) {
      myLinkedText.setTransparent(!((RepositoryNode)parent).isChecked());
    }
    myLinkedText.render(renderer);
  }
}
package com.intellij.internal.psiView.formattingblocks;

import com.intellij.ui.treeStructure.SimpleTreeStructure;

public class BlockTreeStructure extends SimpleTreeStructure {
  private BlockTreeNode myRoot;

  @Override
  public BlockTreeNode getRootElement() {
    return myRoot;
  }

  public void setRoot(BlockTreeNode root) {
    myRoot = root;
  }
}

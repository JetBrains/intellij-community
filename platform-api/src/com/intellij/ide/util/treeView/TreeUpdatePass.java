package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.ActiveRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

class TreeUpdatePass {

  private DefaultMutableTreeNode myNode;

  private ActiveRunnable myBefore;
  private ActiveRunnable myAfter;

  private long myUpdateStamp;
  private boolean myExpired;

  public TreeUpdatePass(@NotNull final DefaultMutableTreeNode node, @Nullable final ActiveRunnable before, @Nullable final ActiveRunnable after) {
    myNode = node;
    myBefore = before;
    myAfter = after;
  }

  public TreeUpdatePass(@NotNull final DefaultMutableTreeNode node) {
    this(node, null, null);
  }

  public DefaultMutableTreeNode getNode() {
    return myNode;
  }

  public void setSheduleStamp(final long updateCount) {
    myUpdateStamp = updateCount;
  }

  public long getUpdateStamp() {
    return myUpdateStamp;
  }

  public void expire() {
    myExpired = true;
  }

  public boolean isExpired() {
    return myExpired;
  }

  @Override
  public String toString() {
    return "TreUpdatePass node=" + myNode + " stamp=" + myUpdateStamp + " expired=" + myExpired;
  }
}
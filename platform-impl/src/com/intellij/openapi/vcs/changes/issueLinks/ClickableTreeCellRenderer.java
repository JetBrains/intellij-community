package com.intellij.openapi.vcs.changes.issueLinks;

import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeCellRenderer;

public interface ClickableTreeCellRenderer extends TreeCellRenderer {
  @Nullable
  Object getTag();
}

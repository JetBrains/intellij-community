package com.intellij.util.ui.tree;

import javax.swing.tree.TreePath;

public interface TreePathState {
  TreePath getRestoredPath();

  TreePathState NULL = new TreePathState() {
    public TreePath getRestoredPath() {
      return null;
    }
  };
}

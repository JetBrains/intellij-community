package com.intellij.ide;

public interface TreeExpander {
  void expandAll();
  boolean canExpand();
  void collapseAll();
  boolean canCollapse();
}

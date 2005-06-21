package com.intellij.ide.util.treeView.smartTree;

public interface Filter extends TreeAction{
  Filter[] EMPTY_ARRAY = new Filter[0];

  boolean isVisible(TreeElement treeNode);

  /**
   *
   * @return this means the filter will work when it is disabled (for example "Show fields" filter is filter hiding fields, but reverted)
   */
  
  boolean isReverted();
}

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 30, 2002
 */
package com.intellij.ide.util.treeView;

import java.util.Comparator;

public class IndexComparator implements Comparator<NodeDescriptor> {
  public static final IndexComparator INSTANCE = new IndexComparator();

  private IndexComparator() {}

  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    return nodeDescriptor1.getIndex() - nodeDescriptor2.getIndex();
  }
}

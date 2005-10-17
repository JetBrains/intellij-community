/*
 * Class NodeComparator
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.nodes;

import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;

import java.util.Comparator;

/**
 * Compares given DebuggerTreeTest by name
 */
public class NodeComparator implements Comparator {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public int compare(Object o1, Object o2) {
    DebuggerTreeNodeImpl node1 = (DebuggerTreeNodeImpl)o1;
    DebuggerTreeNodeImpl node2 = (DebuggerTreeNodeImpl)o2;
    String name1 = node1.getDescriptor().getName();
    String name2 = node2.getDescriptor().getName();
    boolean invalid1 = (name1 == null || (name1.length() > 0 && Character.isDigit(name1.charAt(0))));
    boolean invalid2 = (name2 == null || (name2.length() > 0 && Character.isDigit(name2.charAt(0))));
    if (invalid1) {
      return invalid2? 0 : 1;
    }
    else {
      if (invalid2) return -1;
    }
    if ("this".equals(name1) || "static".equals(name1)) return -1;
    if ("this".equals(name2) || "static".equals(name2)) return 1;
    return name1.compareToIgnoreCase(name2);
  }
}
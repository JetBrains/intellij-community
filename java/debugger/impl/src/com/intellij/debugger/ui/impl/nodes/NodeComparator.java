// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class NodeComparator
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.nodes;

import com.intellij.debugger.ui.tree.DebuggerTreeNode;

import java.util.Comparator;

/**
 * Compares given DebuggerTreeTest by name
 */
public class NodeComparator implements Comparator<DebuggerTreeNode> {
  @Override
  public int compare(final DebuggerTreeNode node1, final DebuggerTreeNode node2) {
    final String name1 = node1.getDescriptor().getName();
    final String name2 = node2.getDescriptor().getName();
    final boolean invalid1 = (name1 == null || (!name1.isEmpty() && Character.isDigit(name1.charAt(0))));
    final boolean invalid2 = (name2 == null || (!name2.isEmpty() && Character.isDigit(name2.charAt(0))));
    if (invalid1) {
      return invalid2 ? 0 : 1;
    }
    else if (invalid2) {
      return -1;
    }
    if ("this".equals(name1) || "static".equals(name1)) {
      return -1;
    }
    if ("this".equals(name2) || "static".equals(name2)) {
      return 1;
    }
    return name1.compareToIgnoreCase(name2);
  }
}
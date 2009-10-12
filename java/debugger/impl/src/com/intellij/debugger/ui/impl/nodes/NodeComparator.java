/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  @SuppressWarnings({"HardCodedStringLiteral"})
  public int compare(final DebuggerTreeNode node1, final DebuggerTreeNode node2) {
    final String name1 = node1.getDescriptor().getName();
    final String name2 = node2.getDescriptor().getName();
    final boolean invalid1 = (name1 == null || (name1.length() > 0 && Character.isDigit(name1.charAt(0))));
    final boolean invalid2 = (name2 == null || (name2.length() > 0 && Character.isDigit(name2.charAt(0))));
    if (invalid1) {
      return invalid2? 0 : 1;
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
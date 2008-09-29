/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;

import java.util.Comparator;

public class WeightBasedComparator implements Comparator<NodeDescriptor> {

  private boolean myCompareToString;

  public static final WeightBasedComparator INSTANCE = new WeightBasedComparator();

  public WeightBasedComparator() {
    this(false);
  }

  public WeightBasedComparator(final boolean compareToString) {
    myCompareToString = compareToString;
  }

  public int compare(NodeDescriptor o1, NodeDescriptor o2) {
    SimpleNode first = (SimpleNode) o1;
    SimpleNode second = (SimpleNode) o2;

    if (myCompareToString && first.getWeight() == second.getWeight()) {
      String s1 = first.toString();
      String s2 = second.toString();
      if (s1 == null) return s2 == null ? 0 : -1;
      if (s2 == null) return +1;
      return s1.compareToIgnoreCase(s2);
    }
    return second.getWeight() - first.getWeight();
  }
}

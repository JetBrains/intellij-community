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

  public static final int UNDEFINED_WEIGHT = Integer.MIN_VALUE;

  private final boolean myCompareToString;

  public static final WeightBasedComparator INSTANCE = new WeightBasedComparator();
  public static final WeightBasedComparator FULL_INSTANCE = new WeightBasedComparator(true) {
    @Override
    protected int compareWeights(final int w1, final int w2) {
      return w1 - w2;
    }
  };

  public WeightBasedComparator() {
    this(false);
  }

  public WeightBasedComparator(final boolean compareToString) {
    myCompareToString = compareToString;
  }

  public int compare(NodeDescriptor o1, NodeDescriptor o2) {
    final int w1 = o1.getWeight();
    final int w2 = o2.getWeight();
    if (myCompareToString && w1 == w2) {
      return compareToString(o1, o2);
    }

    if (w1 == UNDEFINED_WEIGHT && w2 == UNDEFINED_WEIGHT) {
      return o1.getIndex() - o2.getIndex();
    } else {
      return compareWeights(w1, w2);
    }

  }

  protected int compareWeights(final int w1, final int w2) {
    return w2 - w1;
  }

  protected static int compareToString(final NodeDescriptor first, final NodeDescriptor second) {
    String s1 = first.toString();
    String s2 = second.toString();
    if (s1 == null) return s2 == null ? 0 : -1;
    if (s2 == null) return +1;
    return s1.compareToIgnoreCase(s2);
  }
}

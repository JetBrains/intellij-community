// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Comparator;

public class WeightBasedComparator implements Comparator<NodeDescriptor<?>> {

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

  @Override
  public int compare(NodeDescriptor o1, NodeDescriptor o2) {
    final int w1 = getWeight(o1);
    final int w2 = getWeight(o2);
    if (myCompareToString && w1 == w2) {
      return compareToString(o1, o2);
    }

    int weights = compareWeights(w1, w2);
    return weights != 0 ? weights : o1.getIndex() - o2.getIndex();
  }

  protected int getWeight(final NodeDescriptor o1) {
    return o1.getWeight();
  }

  protected int compareWeights(final int w1, final int w2) {
    return w2 - w1;
  }

  protected static int compareToString(final NodeDescriptor first, final NodeDescriptor second) {
    return StringUtil.naturalCompare(first.toString(), second.toString());
  }
}

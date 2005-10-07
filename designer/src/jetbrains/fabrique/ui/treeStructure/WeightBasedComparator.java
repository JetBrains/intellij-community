/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure;

import com.intellij.ide.util.treeView.AlphaComparator;

import java.util.Comparator;

public class WeightBasedComparator implements Comparator {

  public static final WeightBasedComparator INSTANCE = new WeightBasedComparator();

  public int compare(Object o1, Object o2) {
    SimpleNode first = (SimpleNode) o1;
    SimpleNode second = (SimpleNode) o2;

    if (first.getWeight() == second.getWeight()) {
      return AlphaComparator.INSTANCE.compare(first, second);
    }
    else {
      return second.getWeight() - first.getWeight();
    }
  }
}

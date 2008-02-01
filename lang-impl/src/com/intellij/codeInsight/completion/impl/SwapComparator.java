/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import java.util.Comparator;

/**
 * @author peter
*/
class SwapComparator<V> implements Comparator<V> {
  private final Comparator<V> myComparator;

  public SwapComparator(final Comparator<V> comparator) {
    myComparator = comparator;
  }

  public int compare(final V o1, final V o2) {
    return myComparator.compare(o2, o1);
  }
}

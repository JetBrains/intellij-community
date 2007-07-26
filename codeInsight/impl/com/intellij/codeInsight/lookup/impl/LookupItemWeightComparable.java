/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.CompletionPreferencePolicy;

import java.util.Arrays;

/**
 * @author peter
*/
public class LookupItemWeightComparable implements Comparable<LookupItemWeightComparable> {
  private final double myPriority;
  private final int[] myWeight;

  public LookupItemWeightComparable(final double priority, final int[] weight) {
    myPriority = priority;
    myWeight = weight;
  }

  public int compareTo(final LookupItemWeightComparable o) {
    return CompletionPreferencePolicy.doCompare(myPriority, o.myPriority, myWeight, o.myWeight);
  }

  public String toString() {
    return myPriority + " " + Arrays.toString(myWeight);
  }
}

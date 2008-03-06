/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.statistics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class StatisticsInfo implements Comparable<StatisticsInfo>{
  public static final StatisticsInfo EMPTY = new StatisticsInfo("empty", "empty");

  private static final StatisticsManager ourManager = StatisticsManager.getInstance();
  private final String myContext;
  private final String myValue;

  public StatisticsInfo(@NonNls @NotNull final String context, @NonNls @NotNull final String value) {
    myContext = context;
    myValue = value;
  }

  @NotNull
  public String getContext() {
    return myContext;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }

  public int compareTo(final StatisticsInfo o) {
    return getUseCount() - o.getUseCount();
  }

  public void incUseCount() {
    ourManager.incUseCount(this);
  }

  public int getUseCount() {
    return ourManager.getUseCount(this);
  }

  public String toString() {
    return myContext + "::::" + myValue;
  }
}

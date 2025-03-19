// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.statistics;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A couple of strings representing an object for purposes of tracking statistics by {@link StatisticsManager}.
 * Each info consists of "context" and "value", and the manager tracks per each "context", how many times a "value"
 * has occurred in that context and in which order.
 */
public final class StatisticsInfo {
  /**
   * A special value meaning that no statistics should be tracked.
   */
  public static final StatisticsInfo EMPTY = new StatisticsInfo("empty", "empty");

  private final String myContext;
  private final String myValue;
  private final List<StatisticsInfo> myConjuncts;

  public StatisticsInfo(final @NonNls @NotNull String context, final @NonNls @NotNull String value) {
    myContext = context;
    myValue = value;
    myConjuncts = Collections.singletonList(this);
  }

  private StatisticsInfo(String context, String value, List<StatisticsInfo> conjuncts) {
    myContext = context;
    myValue = value;
    myConjuncts = conjuncts;
  }

  public static StatisticsInfo createComposite(List<? extends StatisticsInfo> conjuncts) {
    if (conjuncts.isEmpty()) {
      return EMPTY;
    }

    ArrayList<StatisticsInfo> flattened = new ArrayList<>(conjuncts.size());
    for (StatisticsInfo conjunct : conjuncts) {
      flattened.addAll(conjunct.getConjuncts());
    }
    return new StatisticsInfo(conjuncts.get(0).getContext(), conjuncts.get(0).getValue(), flattened);
  }

  public @NotNull String getContext() {
    return myContext;
  }

  public @NotNull String getValue() {
    return myValue;
  }

  public List<StatisticsInfo> getConjuncts() {
    return myConjuncts;
  }

  @Override
  public String toString() {
    return myContext + "::::" + myValue + (myConjuncts.size() == 1 ? "" : "::::" + myConjuncts);
  }
}

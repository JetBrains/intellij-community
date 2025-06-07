// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.group;

import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates information about grouping rules to use during arrangement.
 * <p/>
 * E.g. a rule might look like 'keep together class methods which implement methods from particular interface'.
 */
public class ArrangementGroupingRule {

  private final @NotNull ArrangementSettingsToken myGroupingType;
  private final @NotNull ArrangementSettingsToken myOrderType;

  public ArrangementGroupingRule(@NotNull ArrangementSettingsToken groupingType) {
    this(groupingType, StdArrangementTokens.Order.KEEP);
  }

  public ArrangementGroupingRule(@NotNull ArrangementSettingsToken groupingType, @NotNull ArrangementSettingsToken orderType) {
    myGroupingType = groupingType;
    myOrderType = orderType;
  }

  public @NotNull ArrangementSettingsToken getGroupingType() {
    return myGroupingType;
  }

  public @NotNull ArrangementSettingsToken getOrderType() {
    return myOrderType;
  }

  @Override
  public ArrangementGroupingRule clone() {
    return new ArrangementGroupingRule(myGroupingType, myOrderType);
  }

  @Override
  public int hashCode() {
    int result = myGroupingType.hashCode();
    result = 31 * result + myOrderType.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrangementGroupingRule rule = (ArrangementGroupingRule)o;

    if (myOrderType != rule.myOrderType) return false;
    if (!myGroupingType.equals(rule.myGroupingType)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("(%s, %s)", myGroupingType, myOrderType);
  }
}

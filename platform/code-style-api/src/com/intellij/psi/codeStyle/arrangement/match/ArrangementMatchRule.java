// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import org.jetbrains.annotations.NotNull;

/**
 * Container for matching strategies to be used during file entries arrangement. 
 * <p/>
 * Example: we can define a rule like 'private final non-static fields' or 'public static methods' etc.
 * <p/>
 * Not thread-safe.
 */
public class ArrangementMatchRule {

  public static final @NotNull ArrangementSettingsToken DEFAULT_ORDER_TYPE = StdArrangementTokens.Order.KEEP;

  private final @NotNull ArrangementEntryMatcher  myMatcher;
  private final @NotNull ArrangementSettingsToken myOrderType;

  public ArrangementMatchRule(@NotNull ArrangementEntryMatcher matcher) {
    this(matcher, DEFAULT_ORDER_TYPE);
  }

  public ArrangementMatchRule(@NotNull ArrangementEntryMatcher matcher, @NotNull ArrangementSettingsToken orderType) {
    myMatcher = matcher;
    myOrderType = orderType;
  }

  public @NotNull ArrangementEntryMatcher getMatcher() {
    return myMatcher;
  }

  public @NotNull ArrangementSettingsToken getOrderType() {
    return myOrderType;
  }

  @Override
  public int hashCode() {
    int result = myMatcher.hashCode();
    result = 31 * result + myOrderType.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrangementMatchRule that = (ArrangementMatchRule)o;
    return myOrderType == that.myOrderType && myMatcher.equals(that.myMatcher);
  }

  @Override
  public String toString() {
    return String.format("matcher: %s, sort type: %s", myMatcher, myOrderType);
  }
}

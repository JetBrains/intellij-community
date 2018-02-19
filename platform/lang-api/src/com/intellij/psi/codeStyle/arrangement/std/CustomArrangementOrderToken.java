// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Allows to define custom ordering rules for entries if none of standard rules can be used.
 *
 * @see StdArrangementTokens.Order
 * @see ArrangementMatchRule#ArrangementMatchRule(ArrangementEntryMatcher, ArrangementSettingsToken)
 * @see ArrangementMatchRule#getOrderType()
 */
public abstract class CustomArrangementOrderToken extends ArrangementSettingsToken {
  protected CustomArrangementOrderToken(@NotNull String id, @NotNull String name) {
    super(id, name);
  }

  @NotNull
  public abstract Comparator<ArrangementEntry> getEntryComparator();
}

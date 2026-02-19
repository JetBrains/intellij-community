// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.std;

import org.jetbrains.annotations.NotNull;

/**
 * Type of {@link StdArrangementSettingsToken}. Defines UI role used to display the token. Used to differentiate between semantically
 * different tokens when constructing matchers.
 * (see {@link com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher.StdMatcherBuilderImpl}).
 */
public class StdArrangementTokenType {
  private final @NotNull StdArrangementTokenUiRole myUiRole;
  private final @NotNull String myId;

  public StdArrangementTokenType(@NotNull StdArrangementTokenUiRole uiRole, @NotNull String id) {
    myUiRole = uiRole;
    myId = id;
  }

  public @NotNull StdArrangementTokenUiRole getUiRole() {
    return myUiRole;
  }

  public boolean is(ArrangementSettingsToken token) {
    return token instanceof StdArrangementSettingsToken && this.equals(((StdArrangementSettingsToken)token).getTokenType());
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {return true;}
    if (!(o instanceof StdArrangementTokenType oType)) {
      return false;
    }
    return oType.myId.equals(myId) && myUiRole == oType.myUiRole;
  }

  public static final String GENERAL_ID = "GENERAL";
  public static final String GROUPING_ID = "GROUPING";
  public static final String MODIFIER_ID = "MODIFIER";
  public static final String REG_EXP_ID = "REG_EXP";
  public static final String ENTRY_TYPE_ID = "ENTRY_TYPE";
  public static final String ORDER_ID = "ORDER";
  public static final String ALIAS_ID = "ALIAS";

  public static final StdArrangementTokenType GENERAL = new StdArrangementTokenType(StdArrangementTokenUiRole.LABEL, GENERAL_ID);
  public static final StdArrangementTokenType GROUPING = new StdArrangementTokenType(StdArrangementTokenUiRole.CHECKBOX, GROUPING_ID);
  public static final StdArrangementTokenType MODIFIER = new StdArrangementTokenType(StdArrangementTokenUiRole.BULB, MODIFIER_ID);
  public static final StdArrangementTokenType REG_EXP = new StdArrangementTokenType(StdArrangementTokenUiRole.TEXT_FIELD, REG_EXP_ID);
  public static final StdArrangementTokenType ENTRY_TYPE = new StdArrangementTokenType(StdArrangementTokenUiRole.BULB, ENTRY_TYPE_ID);
  public static final StdArrangementTokenType ORDER = new StdArrangementTokenType(StdArrangementTokenUiRole.COMBO_BOX, ORDER_ID);
  public static final StdArrangementTokenType ALIAS = new StdArrangementTokenType(StdArrangementTokenUiRole.BULB, ALIAS_ID);
}

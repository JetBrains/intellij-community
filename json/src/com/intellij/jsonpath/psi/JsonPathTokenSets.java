// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.psi;

import com.intellij.psi.tree.TokenSet;

public final class JsonPathTokenSets {
  private JsonPathTokenSets() {
  }
  public static final TokenSet JSONPATH_DOT_NAVIGATION_SET = TokenSet.create(JsonPathTypes.DOT, JsonPathTypes.RECURSIVE_DESCENT);
  public static final TokenSet JSONPATH_EQUALITY_OPERATOR_SET = TokenSet.create(
    JsonPathTypes.EQ_OP,
    JsonPathTypes.NE_OP,
    JsonPathTypes.EEQ_OP,
    JsonPathTypes.ENE_OP
  );
}

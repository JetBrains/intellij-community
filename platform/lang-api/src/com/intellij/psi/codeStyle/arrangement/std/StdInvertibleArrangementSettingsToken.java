// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public final class StdInvertibleArrangementSettingsToken extends StdArrangementSettingsToken implements InvertibleArrangementSettingsToken {
  private static final String NOT = "not ";

  private StdInvertibleArrangementSettingsToken(@NotNull String id,
                                                @NotNull String uiName,
                                                @NotNull StdArrangementTokenType tokenType) {
    super(id, uiName, tokenType);
  }

  @NotNull
  public static StdInvertibleArrangementSettingsToken invertibleTokenById(@NonNls @NotNull String id,
                                                                          @NotNull StdArrangementTokenType tokenType) {
    return new StdInvertibleArrangementSettingsToken(id, StringUtil.toLowerCase(id).replace("_", " "), tokenType);
  }

  @NotNull
  @Override
  public String getInvertedRepresentationValue() {
    return NOT + getRepresentationValue();
  }
}

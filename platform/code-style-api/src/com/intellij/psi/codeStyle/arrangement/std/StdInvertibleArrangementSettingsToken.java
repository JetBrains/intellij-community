// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.std;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public final class StdInvertibleArrangementSettingsToken extends StdArrangementSettingsToken implements InvertibleArrangementSettingsToken {

  private final @Nls @NotNull String myInvertedName;

  private StdInvertibleArrangementSettingsToken(@NotNull String id,
                                                @Nls @NotNull String displayName,
                                                @Nls @NotNull String invertedName,
                                                @NotNull StdArrangementTokenType tokenType) {
    super(id, displayName, tokenType);
    myInvertedName = invertedName;
  }

  @NotNull
  public static StdInvertibleArrangementSettingsToken invertibleToken(@NonNls @NotNull String id,
                                                                      @Nls @NotNull String displayName,
                                                                      @Nls @NotNull String invertedDisplayName,
                                                                      @NotNull StdArrangementTokenType tokenType) {
    return new StdInvertibleArrangementSettingsToken(id, displayName, invertedDisplayName, tokenType);
  }

  @NotNull
  @Override
  public String getInvertedRepresentationValue() {
    return myInvertedName;
  }
}

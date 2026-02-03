// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.CodeStyleBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Represents ArrangementSettingsToken designed for use with standard GUI, i.e. a token that knows its UI role.
 */
public class StdArrangementSettingsToken extends ArrangementSettingsToken {

  private final @NotNull StdArrangementTokenType myTokenType;

  public static @NotNull StdArrangementSettingsToken token(@NotNull String id,
                                                           @Nls @NotNull String name,
                                                           @NotNull StdArrangementTokenType tokenType) {
    return new StdArrangementSettingsToken(id, name, tokenType);
  }

  public static @NotNull StdArrangementSettingsToken tokenByBundle(@NotNull String id,
                                                                   @NotNull @PropertyKey(resourceBundle = CodeStyleBundle.BUNDLE) String key,
                                                                   @NotNull StdArrangementTokenType tokenType) {
    return new StdArrangementSettingsToken(id, CodeStyleBundle.message(key), tokenType);
  }

  public @NotNull StdArrangementTokenType getTokenType() {
    return myTokenType;
  }

  public StdArrangementSettingsToken(@NotNull String id,
                                     @NotNull @Nls String uiName,
                                     @NotNull StdArrangementTokenType tokenType) {
    super(id, uiName);
    myTokenType = tokenType;
  }
}

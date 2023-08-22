// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class CompositeArrangementToken extends StdArrangementSettingsToken {
  private final Set<ArrangementSettingsToken> myParentTokenTypes;

  private CompositeArrangementToken(@NotNull String id,
                                    @Nls @NotNull String uiName,
                                    @NotNull StdArrangementTokenType tokenType,
                                    ArrangementSettingsToken @NotNull ... tokens)
  {
    super(id, uiName, tokenType);
    myParentTokenTypes = ContainerUtil.newHashSet(tokens);
  }

  @NotNull
  public static CompositeArrangementToken create(@NonNls @NotNull String id,
                                                 @Nls @NotNull String displayName,
                                                 @NotNull StdArrangementTokenType tokenType,
                                                 ArrangementSettingsToken @NotNull ... tokens) {
    return new CompositeArrangementToken(id, displayName, tokenType, tokens);
  }

  @NotNull
  public Set<ArrangementSettingsToken> getAdditionalTokens() {
    return myParentTokenTypes;
  }

}

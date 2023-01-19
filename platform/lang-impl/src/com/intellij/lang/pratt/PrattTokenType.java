// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.pratt;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrattTokenType extends IElementType {


  public PrattTokenType(@NotNull @NonNls final String debugName,
                        @Nullable final Language language) {
    super(debugName, language);
  }

  @NotNull
  public @NlsContexts.ParsingError String getExpectedText(final PrattBuilder builder) {
    return LangBundle.message("0.expected", toString());
  }

}

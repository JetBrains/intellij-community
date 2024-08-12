// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrattTokenType extends IElementType {


  public PrattTokenType(final @NotNull @NonNls String debugName,
                        final @Nullable Language language) {
    super(debugName, language);
  }

  public @NotNull @NlsContexts.ParsingError String getExpectedText(final PrattBuilder builder) {
    return LangBundle.message("0.expected", toString());
  }

}

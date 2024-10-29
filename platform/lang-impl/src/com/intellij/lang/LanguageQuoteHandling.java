// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.codeInsight.editorActions.QuoteHandler;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author gregsh
 */
@ApiStatus.Internal
public final class LanguageQuoteHandling extends LanguageExtension<QuoteHandler> {
  public static final LanguageQuoteHandling INSTANCE = new LanguageQuoteHandling();

  private LanguageQuoteHandling() {
    super("com.intellij.lang.quoteHandler");
  }
}

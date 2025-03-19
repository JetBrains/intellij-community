// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class RegExpSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  private final ParserDefinition myParserDefinition;

  public RegExpSyntaxHighlighterFactory() {
    this(RegExpLanguage.INSTANCE);
  }

  protected RegExpSyntaxHighlighterFactory(@NotNull Language language) {
    myParserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
  }


  @Override
  protected @NotNull SyntaxHighlighter createHighlighter() {
    return new RegExpHighlighter(null, myParserDefinition);
  }
}

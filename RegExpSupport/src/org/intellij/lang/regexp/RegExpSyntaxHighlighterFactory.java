// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  protected SyntaxHighlighter createHighlighter() {
    return new RegExpHighlighter(null, myParserDefinition);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.lexer.BasicJavaLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class JavaHighlightingLexer extends AbstractBasicJavaHighlightingLexer {
  public JavaHighlightingLexer(@NotNull LanguageLevel languageLevel) {
    super(
      languageLevel,
      (BasicJavaLexer)JavaParserDefinition.createLexer(languageLevel)
    );
  }

  @Override
  protected void registerDocLayers(@NotNull LayeredLexer docLexer) {
    HtmlHighlightingLexer htmlLexer = new HtmlHighlightingLexer(null);
    htmlLexer.setHasNoEmbeddments(true);
    docLexer.registerLayer(htmlLexer, JavaDocTokenType.DOC_COMMENT_DATA);
    registerSelfStoppingLayer(docLexer, new IElementType[]{JavaDocElementType.DOC_COMMENT}, IElementType.EMPTY_ARRAY);
  }
}
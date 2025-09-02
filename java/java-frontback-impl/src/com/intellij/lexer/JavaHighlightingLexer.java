// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.java.syntax.JavaSyntaxDefinition;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.lang.java.syntax.JavaElementTypeConverterKt.getJavaElementTypeConverter;

public class JavaHighlightingLexer extends AbstractBasicJavaHighlightingLexer {
  public JavaHighlightingLexer(@NotNull LanguageLevel languageLevel) {
    super(languageLevel, (JavaLexer)JavaSyntaxDefinition.createLexer(languageLevel), getJavaElementTypeConverter());
  }

  @Override
  protected void registerDocLayers(@NotNull LayeredLexer docLexer) {
    HtmlLexer htmlLexer = new HtmlLexer(true);
    htmlLexer.setHasNoEmbeddments(true);
    docLexer.registerLayer(htmlLexer, JavaDocTokenType.DOC_COMMENT_DATA);
    registerSelfStoppingLayer(docLexer, new IElementType[]{JavaDocElementType.DOC_COMMENT}, IElementType.EMPTY_ARRAY);
  }
}
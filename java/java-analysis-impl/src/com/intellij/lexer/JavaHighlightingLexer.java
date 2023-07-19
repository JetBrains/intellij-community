// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class JavaHighlightingLexer extends LayeredLexer {
  public JavaHighlightingLexer(@NotNull LanguageLevel languageLevel) {
    super(JavaParserDefinition.createLexer(languageLevel));

    registerSelfStoppingLayer(new JavaStringLiteralLexer('\"', JavaTokenType.STRING_LITERAL, false, "s{"),
                              new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);

    registerSelfStoppingLayer(new JavaStringLiteralLexer('\'', JavaTokenType.STRING_LITERAL, false, "s"),
                              new IElementType[]{JavaTokenType.CHARACTER_LITERAL}, IElementType.EMPTY_ARRAY);

    registerSelfStoppingLayer(new JavaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_LITERAL, true, "s{"),
                              new IElementType[]{JavaTokenType.TEXT_BLOCK_LITERAL}, IElementType.EMPTY_ARRAY);
    
    registerSelfStoppingLayer(new JavaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN, true, "s"),
                              new IElementType[]{JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN}, IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new JavaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_TEMPLATE_MID, true, "s"),
                              new IElementType[]{JavaTokenType.TEXT_BLOCK_TEMPLATE_MID}, IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new JavaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_TEMPLATE_END, true, "s"),
                              new IElementType[]{JavaTokenType.TEXT_BLOCK_TEMPLATE_END}, IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new JavaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.STRING_TEMPLATE_BEGIN, true, "s"),
                              new IElementType[]{JavaTokenType.STRING_TEMPLATE_BEGIN}, IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new JavaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.STRING_TEMPLATE_MID, true, "s"),
                              new IElementType[]{JavaTokenType.STRING_TEMPLATE_MID}, IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new JavaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.STRING_TEMPLATE_END, true, "s"),
                              new IElementType[]{JavaTokenType.STRING_TEMPLATE_END}, IElementType.EMPTY_ARRAY);

    LayeredLexer docLexer = new LayeredLexer(JavaParserDefinition.createDocLexer(languageLevel));
    HtmlHighlightingLexer htmlLexer = new HtmlHighlightingLexer(null);
    htmlLexer.setHasNoEmbeddments(true);
    docLexer.registerLayer(htmlLexer, JavaDocTokenType.DOC_COMMENT_DATA);
    registerSelfStoppingLayer(docLexer, new IElementType[]{JavaDocElementType.DOC_COMMENT}, IElementType.EMPTY_ARRAY);
  }
}
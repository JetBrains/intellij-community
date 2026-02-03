// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.java.syntax.lexer.JavaDocLexer;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaHighlightingLexer extends LayeredLexer {
  public AbstractBasicJavaHighlightingLexer(@NotNull LanguageLevel languageLevel,
                                            @NotNull JavaLexer javaLexer,
                                            @NotNull ElementTypeConverter converter) {
    super(new LexerAdapter(javaLexer, converter));

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

    LayeredLexer docLexer = new LayeredLexer(new LexerAdapter(new JavaDocLexer(languageLevel), converter));

    //noinspection AbstractMethodCallInConstructor
    registerDocLayers(docLexer);
  }

  protected abstract void registerDocLayers(@NotNull LayeredLexer docLexer);
}
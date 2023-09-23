// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lexer;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

public  class LayeredLexerTest extends AbstractBasicLayeredLexerTest {

  @Override
  protected Lexer setupLexer(String text) {
    LayeredLexer lexer = new LayeredLexer(JavaParserDefinition.createLexer(LanguageLevel.JDK_1_3));
    lexer.registerSelfStoppingLayer(new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL),
                                    new IElementType[]{JavaTokenType.STRING_LITERAL},
                                    IElementType.EMPTY_ARRAY);
    lexer.start(text);
    return lexer;
  }
}
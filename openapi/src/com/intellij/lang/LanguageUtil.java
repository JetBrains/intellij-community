/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.lang;

import com.intellij.lexer.Lexer;

public final class LanguageUtil {
  public static ParserDefinition.SpaceRequirements canStickTokensTogetherByLexer(ASTNode left, ASTNode right, Lexer lexer, int lexerState) {
    String textStr = left.getText() + right.getText();

    lexer.start(textStr, 0, textStr.length(), lexerState);
    if(lexer.getTokenType() != left.getElementType()) return ParserDefinition.SpaceRequirements.MUST;
    if(lexer.getTokenEnd() != left.getTextLength()) return ParserDefinition.SpaceRequirements.MUST;
    lexer.advance();
    if(lexer.getTokenEnd() != textStr.length()) return ParserDefinition.SpaceRequirements.MUST;
    if(lexer.getTokenType() != right.getElementType()) return ParserDefinition.SpaceRequirements.MUST;
    return ParserDefinition.SpaceRequirements.MAY;
  }
}

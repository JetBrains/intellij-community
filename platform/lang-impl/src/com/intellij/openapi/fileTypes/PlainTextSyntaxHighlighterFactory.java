/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.ide.highlighter.custom.AbstractCustomLexer;
import com.intellij.ide.highlighter.custom.CustomHighlighterColors;
import com.intellij.ide.highlighter.custom.tokens.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author peter
 */
public class PlainTextSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
    return new SyntaxHighlighterBase() {
      @NotNull
      @Override
      public Lexer getHighlightingLexer() {
        ArrayList<TokenParser> tokenParsers = new ArrayList<TokenParser>();
        tokenParsers.add(new WhitespaceParser());
        tokenParsers.add(new NumberParser("", false));
        tokenParsers.add(new PunctuationParser());
        tokenParsers.add(new IdentifierParser());

        tokenParsers.addAll(BraceTokenParser.BRACES);
        tokenParsers.addAll(BraceTokenParser.PARENS);
        tokenParsers.addAll(BraceTokenParser.BRACKETS);
        tokenParsers.addAll(BraceTokenParser.ANGLE_BRACKETS);

        return new AbstractCustomLexer(tokenParsers);
      }

      @NotNull
      @Override
      public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType == CustomHighlighterTokenType.NUMBER) {
          return new TextAttributesKey[]{CustomHighlighterColors.CUSTOM_NUMBER_ATTRIBUTES};
        }
        return EMPTY;
      }
    };
  }
}
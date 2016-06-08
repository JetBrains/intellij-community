/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.css;

import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class MinifiedFilesUtil {

  private MinifiedFilesUtil() {
  }

  private static final int MAX_OFFSET = 2048; // this is how far we look through the file

  private static final int MIN_SIZE = 150; // file should be large enough to be considered as minified (only non-comment text counts)

  private static final int MIN_LINE_LENGTH = 100; // if there's compact line that is long enough, file is considered to be minified

  private static final double MAX_UNNEEDED_OFFSET_PERCENTAGE = 0.01;
  
  private static final int COUNT_OF_CONSIDERING_CHARACTERS_FROM_END_OF_FILE = 400;

  /**
   * Finds out whether the file minified by using common (not language-specific) heuristics.
   * Can be used for checking of css/less/scss/sass and js files.
   *
   * @param fileContent              target file content
   * @param parserDefinition         Parser definition of target language
   * @param noWSRequireAfterTokenSet TokenSet of types that doesn't require whitespaces after them.
   */
  public static boolean isMinified(@NotNull CharSequence fileContent,
                                   @NotNull ParserDefinition parserDefinition,
                                   @NotNull TokenSet noWSRequireAfterTokenSet) {
    Lexer lexer = parserDefinition.createLexer(null);
    lexer.start(fileContent);
    if (!isMinified(lexer, parserDefinition, noWSRequireAfterTokenSet)) {
      return false;
    }
    else if (lexer.getTokenType() == null) {
      // whole file had been considered
      return true;
    }

    int startOffset = fileContent.length() - COUNT_OF_CONSIDERING_CHARACTERS_FROM_END_OF_FILE;
    if (startOffset <= 0) {
      return true;
    }
    lexer.start(fileContent, startOffset, fileContent.length());
    return isMinified(lexer, parserDefinition, noWSRequireAfterTokenSet);
  }

  protected static boolean isMinified(Lexer lexer, ParserDefinition parserDefinition, TokenSet noWSRequireAfterTokenSet) {
    int offsetIgnoringComments = 0;
    int offsetIgnoringCommentsAndStrings = 0;
    int lineLength = 0;
    int unneededWhitespaceCount = 0;
    String lastTokenText = null;
    IElementType lastTokenType = null;
    TokenSet whitespaceTokens = parserDefinition.getWhitespaceTokens();
    TokenSet stringLiteralElements = parserDefinition.getStringLiteralElements();
    TokenSet commentTokens = parserDefinition.getCommentTokens();
    for (IElementType tokenType = lexer.getTokenType(); tokenType != null; lexer.advance(), tokenType = lexer.getTokenType()) {
      if (commentTokens.contains(tokenType)) {
        lastTokenType = tokenType;
        lastTokenText = lexer.getTokenText();
        continue;
      }

      int tokenLength = lexer.getTokenEnd() - lexer.getTokenStart();
      offsetIgnoringComments += tokenLength;
      if (stringLiteralElements.contains(tokenType)) {
        lineLength += tokenLength;
        lastTokenType = tokenType;
        lastTokenText = lexer.getTokenText();
        continue;
      }
      offsetIgnoringCommentsAndStrings += tokenLength;

      if (whitespaceTokens.contains(tokenType)) {
        if (!commentTokens.contains(lastTokenType) && tokenLength > 1) {
          lexer.advance();
          if (lexer.getTokenType() == null) {
            // it was last token
            break;
          } else {
            return false;
          }
        }

        if (tokenLength == 1 && StringUtil.equals(lastTokenText, "\n") && StringUtil.equals(lexer.getTokenText(), "\n")) {
          unneededWhitespaceCount++;
        }
        else if (tokenLength > 0 && noWSRequireAfterTokenSet.contains(lastTokenType)) {
          unneededWhitespaceCount++;
        }

        if (lexer.getTokenText().contains("\n")) {
          if (lineLength > MIN_LINE_LENGTH) {
            break;
          }
          lineLength = 0;
        }
      }
      else {
        lineLength += tokenLength;
      }

      if (offsetIgnoringComments >= MAX_OFFSET) {
        break;
      }
      lastTokenType = tokenType;
      lastTokenText = lexer.getTokenText();
    }

    return offsetIgnoringComments >= MIN_SIZE &&
           (unneededWhitespaceCount + 0.0d) / offsetIgnoringCommentsAndStrings < MAX_UNNEEDED_OFFSET_PERCENTAGE;
  }

  public static boolean isMinified(@NotNull CharSequence fileContent, @NotNull ParserDefinition parserDefinition) {
    return isMinified(fileContent, parserDefinition, TokenSet.EMPTY);
  }
}

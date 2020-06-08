// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.css;

import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class MinifiedFilesUtil {

  private MinifiedFilesUtil() {
  }

  private static final int MAX_OFFSET = 2048; // this is how far we look through the file

  private static final int MIN_SIZE = 150; // file should be large enough to be considered as minified (only non-comment text counts)

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
                                   @NotNull TokenSet noWSRequireBeforeTokenSet,
                                   @NotNull TokenSet noWSRequireAfterTokenSet) {
    return isMinified(fileContent, parserDefinition, noWSRequireBeforeTokenSet, noWSRequireAfterTokenSet,
                      parserDefinition.getStringLiteralElements());
  }


    /**
     * Finds out whether the file minified by using common (not language-specific) heuristics.
     * Can be used for checking of css/less/scss/sass and js files.
     *
     * @param fileContent              target file content
     * @param parserDefinition         Parser definition of target language
     * @param noWSRequireAfterTokenSet TokenSet of types that doesn't require whitespaces after them.
     * @param stringsTokenSet TokenSet of types considered as string elements
     */
  public static boolean isMinified(@NotNull CharSequence fileContent,
                                   @NotNull ParserDefinition parserDefinition,
                                   @NotNull TokenSet noWSRequireBeforeTokenSet,
                                   @NotNull TokenSet noWSRequireAfterTokenSet,
                                   @NotNull TokenSet stringsTokenSet) {
    Lexer lexer = parserDefinition.createLexer(null);
    lexer.start(fileContent);
    if (!isMinified(lexer, parserDefinition, noWSRequireBeforeTokenSet, noWSRequireAfterTokenSet, stringsTokenSet)) {
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

    while (lexer.getTokenType() != null && lexer.getTokenStart() < startOffset) lexer.advance();
    if (lexer.getTokenType() == null || (fileContent.length() - lexer.getTokenStart() < MIN_SIZE * 2)) {
      return true;
    }

    return isMinified(lexer, parserDefinition, noWSRequireBeforeTokenSet, noWSRequireAfterTokenSet, stringsTokenSet);
  }

  protected static boolean isMinified(@NotNull Lexer lexer,
                                      @NotNull ParserDefinition parserDefinition,
                                      @NotNull TokenSet noWSRequireBeforeTokenSet,
                                      @NotNull TokenSet noWSRequireAfterTokenSet,
                                      @NotNull TokenSet stringLiteralElements) {
    int offsetIgnoringComments = 0;
    int offsetIgnoringCommentsAndStrings = 0;
    int unneededWhitespaceCount = 0;
    String lastTokenText = null;
    IElementType lastTokenType = null;
    TokenSet whitespaceTokens = parserDefinition.getWhitespaceTokens();
    TokenSet commentTokens = parserDefinition.getCommentTokens();
    boolean lastWhiteSpaceWasHandled = false;
    for (IElementType tokenType = lexer.getTokenType(); tokenType != null; lexer.advance(), tokenType = lexer.getTokenType()) {
      if (commentTokens.contains(tokenType)) {
        lastTokenType = tokenType;
        lastTokenText = lexer.getTokenText();
        continue;
      }

      int tokenLength = lexer.getTokenEnd() - lexer.getTokenStart();
      if (isNewLine(lexer, tokenLength) && commentTokens.contains(lastTokenType) && !noWSRequireAfterTokenSet.contains(lastTokenType)) {
        // do not count new line after line comment token since it's required and it's part of comment
        continue;
      }

      offsetIgnoringComments += tokenLength;
      if (!stringLiteralElements.contains(tokenType)) {
        offsetIgnoringCommentsAndStrings += tokenLength;
      }

      if (whitespaceTokens.contains(tokenType)) {
        lastWhiteSpaceWasHandled = false;
        if (tokenLength > 1 && !commentTokens.contains(lastTokenType)) {
          lexer.advance();
          if (lexer.getTokenType() == null) {
            // it was last token
            break;
          } else {
            return false;
          }
        }

        if (isNewLine(lexer, tokenLength) && StringUtil.equals(lastTokenText, "\n") ||
            tokenLength > 0 && noWSRequireAfterTokenSet.contains(lastTokenType)) {
          unneededWhitespaceCount++;
          lastWhiteSpaceWasHandled = true;
        }
      }
      else {
        if (!lastWhiteSpaceWasHandled && whitespaceTokens.contains(lastTokenType)
            && StringUtil.isNotEmpty(lastTokenText) && noWSRequireBeforeTokenSet.contains(tokenType)) {
          unneededWhitespaceCount++;
        }
      }

      if (stringLiteralElements.contains(tokenType)) {
        lastTokenType = tokenType;
        lastTokenText = lexer.getTokenText();
        continue;
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

  private static boolean isNewLine(@NotNull Lexer lexer, int tokenLength) {
    return tokenLength == 1 && StringUtil.equals(lexer.getTokenText(), "\n");
  }

  public static boolean isMinified(@NotNull CharSequence fileContent, @NotNull ParserDefinition parserDefinition) {
    return isMinified(fileContent, parserDefinition, TokenSet.EMPTY, TokenSet.EMPTY);
  }
}

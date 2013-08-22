package com.intellij.psi.css;

import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * User: zolotov
 * Date: 2/19/13
 */

public class MinifiedFilesUtil {
  private MinifiedFilesUtil() {
  }

  private static final int MAX_OFFSET = 2048; // this is how far we look through the file

  private static final int MIN_SIZE = 150; // file should be large enough to be considered as minified (only non-comment text counts)

  private static final int MIN_LINE_LENGTH = 100; // if there's compact line that is long enough, file is considered to be minified

  private static final double MAX_UNNEEDED_OFFSET_PERCENTAGE = 0.01;

  /**
   * Finds out whether the file minified by using common (not language-specific) heuristics.
   * Can be used for checking of css/less/scss/sass and js files.
   *
   * @param lexer                    Lexer started on content of target file
   * @param parserDefinition         Parser definition of target language
   * @param noWSRequireAfterTokenSet TokenSet of types that doesn't require whitespaces after them.
   */
  public static boolean isMinified(Lexer lexer,
                                   ParserDefinition parserDefinition,
                                   TokenSet noWSRequireAfterTokenSet) {
    int offsetIgnoringComments = 0;
    int offsetIgnoringCommentsAndStrings = 0;
    int lineLength = 0;
    int unneededWhitespaceCount = 0;
    IElementType lastTokenType = null;
    TokenSet whitespaceTokens = parserDefinition.getWhitespaceTokens();
    TokenSet stringLiteralElements = parserDefinition.getStringLiteralElements();
    TokenSet commentTokens = parserDefinition.getCommentTokens();
    for (IElementType tokenType = lexer.getTokenType(); tokenType != null; lexer.advance(), tokenType = lexer.getTokenType()) {
      if (commentTokens.contains(tokenType)) {
        lastTokenType = tokenType;
        continue;
      }

      int tokenLength = lexer.getTokenEnd() - lexer.getTokenStart();
      offsetIgnoringComments += tokenLength;
      if (stringLiteralElements.contains(tokenType)) {
        lineLength += tokenLength;
        lastTokenType = tokenType;
        continue;
      }
      offsetIgnoringCommentsAndStrings += tokenLength;

      if (whitespaceTokens.contains(tokenType)) {
        if (!commentTokens.contains(lastTokenType) && tokenLength > 1) {
          return false;
        }

        if (lexer.getTokenText().contains("\n")) {
          if (lineLength > MIN_LINE_LENGTH) {
            break;
          }
          lineLength = 0;
        }

        if (" ".equals(lexer.getTokenText()) && noWSRequireAfterTokenSet.contains(lastTokenType)) {
          unneededWhitespaceCount++;
        }
      }
      else {
        lineLength += tokenLength;
      }

      if (offsetIgnoringComments >= MAX_OFFSET) {
        break;
      }
      lastTokenType = tokenType;
    }

    return offsetIgnoringComments >= MIN_SIZE && (unneededWhitespaceCount + 0.0d) / offsetIgnoringCommentsAndStrings < MAX_UNNEEDED_OFFSET_PERCENTAGE;
  }

  public static boolean isMinified(Lexer lexer, ParserDefinition parserDefinition) {
    return isMinified(lexer, parserDefinition, TokenSet.EMPTY);
  }
}

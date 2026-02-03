// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.search.scope.packageSet;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.search.scope.packageSet.lexer.ScopeTokenTypes;
import org.jetbrains.annotations.Nullable;

public class FilePackageSetParserExtension implements PackageSetParserExtension {

  @Override
  public @Nullable String parseScope(Lexer lexer) {
    if (lexer.getTokenType() != ScopeTokenTypes.IDENTIFIER) return null;
    String id = getTokenText(lexer);
    if (FilePatternPackageSet.SCOPE_FILE.equals(id) || FilePatternPackageSet.SCOPE_EXT.equals(id)) {

      final CharSequence buf = lexer.getBufferSequence();
      final int end = lexer.getTokenEnd();
      final int bufferEnd = lexer.getBufferEnd();

      if (end >= bufferEnd || buf.charAt(end) != ':' && buf.charAt(end) != '[') {
        return null;
      }

      lexer.advance();
      return FilePatternPackageSet.SCOPE_FILE.equals(id) ? FilePatternPackageSet.SCOPE_FILE : FilePatternPackageSet.SCOPE_EXT;
    }
    return null;
  }

  @Override
  public @Nullable PackageSet parsePackageSet(final Lexer lexer, final String scope, final String modulePattern) throws ParsingException {
    if (!FilePatternPackageSet.SCOPE_FILE.equals(scope) && !FilePatternPackageSet.SCOPE_EXT.equals(scope)) return null;
    return new FilePatternPackageSet(modulePattern, parseFilePattern(lexer), FilePatternPackageSet.SCOPE_FILE.equals(scope));
  }

  protected static String parseFilePattern(Lexer lexer) throws ParsingException {
    StringBuilder pattern = new StringBuilder();
    boolean wasIdentifier = false;
    while (true) {
      if (lexer.getTokenType() == ScopeTokenTypes.DIV) {
        wasIdentifier = false;
        pattern.append("/");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.IDENTIFIER || lexer.getTokenType() == ScopeTokenTypes.INTEGER_LITERAL) {
        if (wasIdentifier) error(lexer, CodeInsightBundle.message("error.package.set.token.expectations", getTokenText(lexer)));
        wasIdentifier = lexer.getTokenType() == ScopeTokenTypes.IDENTIFIER;
        pattern.append(getTokenText(lexer));
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.ASTERISK) {
        wasIdentifier = false;
        pattern.append("*");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.DOT) {
        wasIdentifier = false;
        pattern.append(".");
      }
      else if (lexer.getTokenType() == TokenType.WHITE_SPACE) {
        wasIdentifier = false;
        pattern.append(" ");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.MINUS) {
        wasIdentifier = false;
        pattern.append("-");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.TILDE) {
        wasIdentifier = false;
        pattern.append("~");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.SHARP) {
        wasIdentifier = false;
        pattern.append("#");
      }
      else {
        break;
      }
      lexer.advance();
    }

    if (pattern.isEmpty()) {
      error(lexer, CodeInsightBundle.message("error.package.set.pattern.expectations"));
    }

    return pattern.toString();
  }

  private static String getTokenText(Lexer lexer) {
    int start = lexer.getTokenStart();
    int end = lexer.getTokenEnd();
    return lexer.getBufferSequence().subSequence(start, end).toString();
  }

  private static void error(Lexer lexer, String message) throws ParsingException {
    throw new ParsingException(
      CodeInsightBundle.message("error.package.set.position.parsing.error", message, (lexer.getTokenStart() + 1)));
  }
}
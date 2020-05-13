// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search.scope.packageSet;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lexer.Lexer;
import com.intellij.psi.search.scope.packageSet.lexer.ScopeTokenTypes;

public class PatternPackageSetParserExtension implements PackageSetParserExtension {

  @Override
  public PackageSet parsePackageSet(final Lexer lexer, final String scope, final String modulePattern) throws ParsingException {
    /*if (scope == PatternPackageSet.SCOPE_ANY && modulePattern == null) {
      error(AnalysisScopeBundle.message("error.packageset.common.expectations"), lexer);
    }*/
    if (scope != PatternPackageSet.SCOPE_ANY &&
        scope != PatternPackageSet.SCOPE_LIBRARY &&
        scope != PatternPackageSet.SCOPE_PROBLEM &&
        scope != PatternPackageSet.SCOPE_SOURCE &&
        scope != PatternPackageSet.SCOPE_TEST) {
      return null;
    }
    return new PatternPackageSet(parseAspectJPattern(lexer), scope, modulePattern);
  }

  @Override
  public String parseScope(final Lexer lexer) {
    if (lexer.getTokenType() != ScopeTokenTypes.IDENTIFIER) return PatternPackageSet.SCOPE_ANY;
    String id = getTokenText(lexer);
    String scope = PatternPackageSet.SCOPE_ANY;
    if (PatternPackageSet.SCOPE_SOURCE.equals(id)) {
      scope = PatternPackageSet.SCOPE_SOURCE;
    } else if (PatternPackageSet.SCOPE_TEST.equals(id)) {
      scope = PatternPackageSet.SCOPE_TEST;
    } else if (PatternPackageSet.SCOPE_PROBLEM.equals(id)) {
      scope = PatternPackageSet.SCOPE_PROBLEM;
    } else if (PatternPackageSet.SCOPE_LIBRARY.equals(id)) {
      scope = PatternPackageSet.SCOPE_LIBRARY;
    } else if (!id.trim().isEmpty()) {
      scope = null;
    }
    final CharSequence buf = lexer.getBufferSequence();
    int end = lexer.getTokenEnd();
    int bufferEnd = lexer.getBufferEnd();

    if (scope == PatternPackageSet.SCOPE_ANY || end >= bufferEnd || buf.charAt(end) != ':' && buf.charAt(end) != '[') {
      return PatternPackageSet.SCOPE_ANY;
    }

    if (scope != null) {
      lexer.advance();
    }

    return scope;
  }

  private static String parseAspectJPattern(Lexer lexer) throws ParsingException {
    StringBuilder pattern = new StringBuilder();
    boolean wasIdentifier = false;
    while (true) {
      if (lexer.getTokenType() == ScopeTokenTypes.DOT) {
        pattern.append('.');
        wasIdentifier = false;
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.ASTERISK) {
        pattern.append('*');
        wasIdentifier = false;
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.IDENTIFIER) {
        if (wasIdentifier) error(CodeInsightBundle.message("error.package.set.token.expectations", getTokenText(lexer)), lexer);
        wasIdentifier = true;
        pattern.append(getTokenText(lexer));
      }
      else {
        break;
      }
      lexer.advance();
    }

    if (pattern.length() == 0) {
      error(CodeInsightBundle.message("error.package.set.pattern.expectations"), lexer);
    }

    return pattern.toString();
  }


  private static String getTokenText(Lexer lexer) {
    int start = lexer.getTokenStart();
    int end = lexer.getTokenEnd();
    return lexer.getBufferSequence().subSequence(start, end).toString();
  }

  private static void error(String message, Lexer lexer) throws ParsingException {
    throw new ParsingException(
      CodeInsightBundle.message("error.package.set.position.parsing.error", message, (lexer.getTokenStart() + 1)));
  }
}

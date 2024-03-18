// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search.scope.packageSet;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.search.scope.packageSet.lexer.ScopeTokenTypes;
import com.intellij.util.containers.ContainerUtil;

public final class PatternPackageSetParserExtension implements PackageSetParserExtension {

  @Override
  public PackageSet parsePackageSet(final Lexer lexer, final String scope, final String modulePattern) throws ParsingException {
    /*if (scope == PatternPackageSet.SCOPE_ANY && modulePattern == null) {
      error(AnalysisScopeBundle.message("error.packageset.common.expectations"), lexer);
    }*/
    PatternPackageSet.Scope scopeByText = ContainerUtil.find(PatternPackageSet.Scope.values(), 
                                                             value -> Strings.areSameInstance(value.getId(), scope));
    if (scopeByText == null) {
      return null;
    }
    return new PatternPackageSet(parseAspectJPattern(lexer), scopeByText, modulePattern);
  }

  @Override
  public String parseScope(final Lexer lexer) {
    if (lexer.getTokenType() != ScopeTokenTypes.IDENTIFIER) return PatternPackageSet.Scope.ANY.getId();
    String id = getTokenText(lexer);
    PatternPackageSet.Scope scope;
    if (id.trim().isEmpty()) {
      scope = PatternPackageSet.Scope.ANY;
    }
    else {
      scope = ContainerUtil.find(PatternPackageSet.Scope.values(), value -> value.getId().equals(id));
    }
    final CharSequence buf = lexer.getBufferSequence();
    int end = lexer.getTokenEnd();
    int bufferEnd = lexer.getBufferEnd();

    if (scope == PatternPackageSet.Scope.ANY || end >= bufferEnd || buf.charAt(end) != ':' && buf.charAt(end) != '[') {
      return PatternPackageSet.Scope.ANY.getId();
    }

    if (scope != null) {
      lexer.advance();
      return scope.getId();
    }
    return null;
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

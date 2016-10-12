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

/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.analysis.AnalysisScopeBundle;
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
    StringBuffer pattern = new StringBuffer();
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
        if (wasIdentifier) error(AnalysisScopeBundle.message("error.package.set.token.expectations", getTokenText(lexer)), lexer);
        wasIdentifier = true;
        pattern.append(getTokenText(lexer));
      }
      else {
        break;
      }
      lexer.advance();
    }

    if (pattern.length() == 0) {
      error(AnalysisScopeBundle.message("error.package.set.pattern.expectations"), lexer);
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
      AnalysisScopeBundle.message("error.package.set.position.parsing.error", message, (lexer.getTokenStart() + 1)));
  }
}

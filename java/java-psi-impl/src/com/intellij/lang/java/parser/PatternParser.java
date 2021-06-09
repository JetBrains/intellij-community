// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.*;

public class PatternParser {
  private static final TokenSet PATTERN_MODIFIERS = TokenSet.create(JavaTokenType.FINAL_KEYWORD);

  private final JavaParser myParser;

  public PatternParser(JavaParser javaParser) {
    myParser = javaParser;
  }

  /**
   * Checks whether given token sequence can be parsed as a pattern.
   * The result of the method makes sense only for places where pattern is expected (case label and instanceof expression).
   */
  @Contract(pure = true)
  public boolean isPattern(final PsiBuilder builder) {
    PsiBuilder.Marker patternStart = builder.mark();
    while (builder.getTokenType() == JavaTokenType.LPARENTH) {
      builder.advanceLexer();
    }
    myParser.getDeclarationParser().parseModifierList(builder, PATTERN_MODIFIERS);
    PsiBuilder.Marker type = myParser.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD);
    boolean isPattern = type != null && builder.getTokenType() == JavaTokenType.IDENTIFIER;
    patternStart.rollbackTo();
    return isPattern;
  }

  PsiBuilder.@NotNull Marker parsePattern(final PsiBuilder builder) {
    PsiBuilder.Marker guardPattern = builder.mark();
    PsiBuilder.Marker primaryPattern = parsePrimaryPattern(builder);
    if (builder.getTokenType() != JavaTokenType.ANDAND) {
      guardPattern.drop();
      return primaryPattern;
    }
    builder.advanceLexer();
    PsiBuilder.Marker guardingExpression = myParser.getExpressionParser().parseAssignment(builder);
    if (guardingExpression == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
    }
    done(guardPattern, JavaElementType.GUARDED_PATTERN);
    return guardPattern;
  }

  PsiBuilder.@NotNull Marker parsePrimaryPattern(final PsiBuilder builder) {
    if (builder.getTokenType() == JavaTokenType.LPARENTH) {
      PsiBuilder.Marker parenPattern = builder.mark();
      builder.advanceLexer();
      parsePattern(builder);
      if (!expect(builder, JavaTokenType.RPARENTH)) {
        error(builder, JavaPsiBundle.message("expected.rparen"));
      }
      done(parenPattern, JavaElementType.PARENTHESIZED_PATTERN);
      return parenPattern;
    }
    return parseTypePattern(builder);
  }

  private PsiBuilder.@NotNull Marker parseTypePattern(final PsiBuilder builder) {
    PsiBuilder.Marker pattern = builder.mark();
    PsiBuilder.Marker patternVariable = builder.mark();
    myParser.getDeclarationParser().parseModifierList(builder, PATTERN_MODIFIERS);

    PsiBuilder.Marker type = myParser.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD);
    // guarded by isPattern
    assert type != null;
    assert expect(builder, JavaTokenType.IDENTIFIER);
    done(patternVariable, JavaElementType.PATTERN_VARIABLE);
    done(pattern, JavaElementType.TYPE_TEST_PATTERN);
    return pattern;
  }
}

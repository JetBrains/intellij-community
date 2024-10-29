// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Function;

public class BasicExpressionParser {
  private static final boolean useNewImplementation = Registry.is("pratt.java.expression.parser", true);

  private final BasicOldExpressionParser myOldExpressionParser;
  private final BasicPrattExpressionParser myNewExpressionParser;

  public static final TokenSet SHIFT_OPS = TokenSet.create(JavaTokenType.LTLT, JavaTokenType.GTGT, JavaTokenType.GTGTGT);
  public static final TokenSet ADDITIVE_OPS = TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS);
  public static final TokenSet MULTIPLICATIVE_OPS = TokenSet.create(JavaTokenType.ASTERISK, JavaTokenType.DIV, JavaTokenType.PERC);
  public static final TokenSet ASSIGNMENT_OPS = TokenSet.create(
    JavaTokenType.EQ, JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ, JavaTokenType.PERCEQ, JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ,
    JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ, JavaTokenType.GTGTGTEQ, JavaTokenType.ANDEQ, JavaTokenType.OREQ, JavaTokenType.XOREQ);
  static final int FORBID_LAMBDA_MASK = 0x1;

  private final BasicJavaParser myParser;


  public BasicExpressionParser(@NotNull BasicJavaParser parser,
                               @NotNull BasicOldExpressionParser oldExpressionParser,
                               @NotNull BasicPrattExpressionParser prattExpressionParser) {
    myOldExpressionParser = oldExpressionParser;
    myNewExpressionParser = prattExpressionParser;
    myParser = parser;
  }

  @Nullable
  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    if (useNewImplementation) {
      return myNewExpressionParser.parse(builder);
    }
    return myOldExpressionParser.parse(builder);
  }

  PsiBuilder.Marker parseConditionalAndForbiddingLambda(final PsiBuilder builder) {
    if (useNewImplementation) {
      return myNewExpressionParser.tryParseWithPrecedenceAtMost(builder, BasicPrattExpressionParser.CONDITIONAL_EXPR_PRECEDENCE,
                                                                FORBID_LAMBDA_MASK);
    }
    return myOldExpressionParser.parseConditionalAnd(builder, FORBID_LAMBDA_MASK);
  }

  public PsiBuilder.Marker parseArgumentList(final PsiBuilder builder) {
    if (useNewImplementation) {
      return myNewExpressionParser.parseArgumentList(builder);
    }
    return myOldExpressionParser.parseArgumentList(builder);
  }

  PsiBuilder.Marker parseArrayInitializer(@NotNull PsiBuilder builder,
                                          @NotNull IElementType type,
                                          @NotNull Function<? super PsiBuilder, PsiBuilder.Marker> elementParser,
                                          @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String missingElementKey) {
    if (useNewImplementation) {
      return myNewExpressionParser.parseArrayInitializer(builder, type, elementParser, missingElementKey);
    }
    return myOldExpressionParser.parseArrayInitializer(builder, type, elementParser, missingElementKey);
  }

  PsiBuilder.Marker parseConditional(final PsiBuilder builder) {
    if (useNewImplementation) {
      return myNewExpressionParser.tryParseWithPrecedenceAtMost(builder, BasicPrattExpressionParser.CONDITIONAL_EXPR_PRECEDENCE,
                                                                FORBID_LAMBDA_MASK);
    }
    return myOldExpressionParser.parseConditional(builder, 0);
  }

  PsiBuilder.Marker parseAssignmentForbiddingLambda(final PsiBuilder builder) {
    if (useNewImplementation) {
      return myNewExpressionParser.parse(builder, FORBID_LAMBDA_MASK);
    }
    return myOldExpressionParser.parseAssignment(builder, FORBID_LAMBDA_MASK);
  }

  /**
   * @deprecated plugin compatibility, use the one from the StatementParser
   */
  @Nullable
  @Deprecated
  public PsiBuilder.Marker parseCaseLabel(@NotNull PsiBuilder builder) {
    return myParser.getStatementParser().parseCaseLabel(builder).first;
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Function;

/**
 * @deprecated Use the new Java syntax library instead.
 * See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class ExpressionParser {
  static final int FORBID_LAMBDA_MASK = 0x1;
  private static final boolean useNewImplementation = Registry.is("pratt.java.expression.parser", true);
  private final OldExpressionParser myOldExpressionParser;
  private final PrattExpressionParser myNewExpressionParser;
  private final JavaParser myParser;

  public ExpressionParser(@NotNull JavaParser javaParser) {
    @NotNull OldExpressionParser oldExpressionParser = new OldExpressionParser(javaParser);
    @NotNull PrattExpressionParser prattExpressionParser = new PrattExpressionParser(javaParser);
    this.myOldExpressionParser = oldExpressionParser;
    this.myNewExpressionParser = prattExpressionParser;
    this.myParser = javaParser;
  }


  @Deprecated
  public @Nullable PsiBuilder.Marker parseCaseLabel(@NotNull PsiBuilder builder) {
    return myParser.getStatementParser().parseCaseLabel(builder).first;
  }

  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    if (useNewImplementation) {
      return myNewExpressionParser.parse(builder);
    }
    return myOldExpressionParser.parse(builder);
  }

  PsiBuilder.Marker parseConditionalAndForbiddingLambda(final PsiBuilder builder) {
    if (useNewImplementation) {
      return myNewExpressionParser.tryParseWithPrecedenceAtMost(builder, PrattExpressionParser.CONDITIONAL_EXPR_PRECEDENCE,
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
      return myNewExpressionParser.tryParseWithPrecedenceAtMost(builder, PrattExpressionParser.CONDITIONAL_EXPR_PRECEDENCE,
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
}
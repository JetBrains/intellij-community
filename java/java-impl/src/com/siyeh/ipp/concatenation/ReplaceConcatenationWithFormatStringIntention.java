// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceConcatenationWithFormatStringIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.concatenation.with.format.string.intention.family.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new Jdk5StringConcatenationPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    PsiElement parent = expression.getParent();
    while (ExpressionUtils.isStringConcatenation(parent)) {
      expression = (PsiPolyadicExpression)parent;
      parent = expression.getParent();
    }
    final List<PsiExpression> formatParameters = new ArrayList<>();
    final String formatString = 
      StringUtil.escapeStringCharacters(PsiConcatenationUtil.buildUnescapedFormatString(expression, true, formatParameters));
    if (replaceWithPrintfExpression(expression, formatString, formatParameters)) {
      return;
    }
    CommentTracker commentTracker = new CommentTracker();
    final StringBuilder newExpression = new StringBuilder();
    if (HighlightingFeature.TEXT_BLOCKS.isAvailable(element)) {
      appendFormatString(expression, formatString, false, newExpression);
      newExpression.append(".formatted(");
    } else {
      newExpression.append("java.lang.String.format(");
      appendFormatString(expression, formatString, false, newExpression);
      if (!formatParameters.isEmpty()) {
        newExpression.append(", ");
      }
    }
    newExpression.append(StreamEx.of(formatParameters).map(commentTracker::text).joining(", "));
    newExpression.append(')');
    PsiReplacementUtil.replaceExpression(expression, newExpression.toString(), commentTracker);
  }

  @Override
  protected String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message(HighlightingFeature.TEXT_BLOCKS.isAvailable(element)
                                            ? "replace.concatenation.with.format.string.intention.name.formatted"
                                            : "replace.concatenation.with.format.string.intention.name");
  }

  private static boolean replaceWithPrintfExpression(PsiPolyadicExpression expression, String formatString,
                                                     List<PsiExpression> formatParameters) {
    final PsiElement expressionParent = expression.getParent();
    if (!(expressionParent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandParent = expressionParent.getParent();
    if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    final boolean insertNewline;
    if ("println".equals(name)) {
      insertNewline = true;
    }
    else if ("print".equals(name)) {
      insertNewline = false;
    }
    else {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    if (!"java.io.PrintStream".equals(qualifiedName) &&
        !"java.io.PrintWriter".equals(qualifiedName)) {
      return false;
    }
    CommentTracker commentTracker = new CommentTracker();
    final StringBuilder newExpression = new StringBuilder();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != null) {
      newExpression.append(commentTracker.text(qualifier)).append('.');
    }
    newExpression.append("printf(");
    appendFormatString(expression, formatString, insertNewline, newExpression);
    for (PsiExpression formatParameter : formatParameters) {
      newExpression.append(",").append(commentTracker.text(formatParameter));
    }
    newExpression.append(')');
    PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString(), commentTracker);
    return true;
  }

  private static void appendFormatString(PsiPolyadicExpression expression,
                                         String formatString,
                                         boolean insertNewline,
                                         StringBuilder newExpression) {
    final boolean textBlocks = ContainerUtil.exists(expression.getOperands(),
                                                    operand -> operand instanceof PsiLiteralExpression literal && literal.isTextBlock());
    if (textBlocks) {
      newExpression.append("\"\"\"\n");
      formatString = Arrays.stream(formatString.split("\n"))
        .map(s -> PsiLiteralUtil.escapeTextBlockCharacters(s))
        .collect(Collectors.joining("\n"));
      newExpression.append(formatString);
      if (insertNewline) {
        newExpression.append('\n');
      }
      newExpression.append("\"\"\"");
    } else {
      newExpression.append('\"').append(formatString);
      if (insertNewline) {
        newExpression.append("%n");
      }
      newExpression.append('\"');
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.codeInsight.DumbAwareAnnotationUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author shkate@jetbrains.com
 */
public class LiteralExpressionTokenizer extends EscapeSequenceTokenizer<PsiLiteralExpression> {
  @Override
  public void tokenize(@NotNull PsiLiteralExpression expression, @NotNull TokenConsumer consumer) {
    String text;
    if (!hasStringType(expression)) {
      text = null;
    }
    else if (expression.isTextBlock()) {
      text = expression.getText();
      if (text.length() < 7) return;
      text = text.substring(3, text.length() - 3);
    }
    else {
      text = PsiLiteralUtil.getStringLiteralContent(expression);
    }
    
    if (StringUtil.isEmpty(text) || text.length() <= 2) { // optimization to avoid expensive injection check
      return;
    }

    if (InjectedLanguageManager.getInstance(expression.getProject()).getInjectedPsiFiles(expression) != null) return;

    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(skipParenthesizedExprUp(expression),
                                                                       PsiModifierListOwner.class);
    if (listOwner != null && !shouldProcessLiteralExpression(expression, listOwner)) {
      if (!DumbService.isDumb(listOwner.getProject()) && AnnotationUtil.isAnnotated(listOwner, AnnotationUtil.NON_NLS, AnnotationUtil.CHECK_EXTERNAL) ||
          DumbAwareAnnotationUtil.hasAnnotation(listOwner, AnnotationUtil.NON_NLS)) {
        return;
      }
    }

    if (!text.contains("\\")) {
      consumer.consumeToken(expression, PlainTextSplitter.getInstance());
    }
    else {
      processTextWithEscapeSequences(expression, text, consumer);
    }
  }

  private static boolean shouldProcessLiteralExpression(@NotNull PsiLiteralExpression expression, PsiModifierListOwner listOwner) {
    PsiElement targetElement = skipParenthesizedExprUp(getCompleteStringValueExpression(expression));
    if (listOwner instanceof PsiMethod) {
      if (Arrays.stream(PsiUtil.findReturnStatements(((PsiMethod)listOwner))).map(s -> s.getReturnValue())
        .anyMatch(e -> e == targetElement)) {
        return false;
      }
    }
    else if (listOwner instanceof PsiVariable psiVariable && psiVariable.getInitializer() == targetElement) return false;

    return true;
  }

  private static PsiElement skipParenthesizedExprUp(PsiElement expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = expression.getParent();
    }
    return expression;
  }

  private static boolean hasStringType(@Nullable PsiLiteralExpression expression) {
    if (expression == null) return false;
    if (!DumbService.isDumb(expression.getProject())) return ExpressionUtils.hasStringType(expression);
    String text = expression.getText();
    return text.startsWith("\"") && text.endsWith("\"");
  }

  private static PsiElement getCompleteStringValueExpression(@NotNull PsiLiteralExpression expression) {
    PsiElement parent = expression.getParent();
    final PsiElement skipParenthesizedExprUpElement = skipParenthesizedExprUp(parent);
    if (!(skipParenthesizedExprUpElement instanceof PsiPolyadicExpression polyadicExpression)) return expression;
    if (!JavaTokenType.PLUS.equals(polyadicExpression.getOperationTokenType())) return expression;

    return parent;
  }

  public static void processTextWithEscapeSequences(PsiLiteralExpression element, String text, TokenConsumer consumer) {
    StringBuilder unescapedText = new StringBuilder(text.length());
    int[] offsets = new int[text.length() + 1];
    CodeInsightUtilCore.parseStringCharacters(text, unescapedText, offsets);

    int startOffset = (element != null && element.isTextBlock()) ? 3 : 1;
    processTextWithOffsets(element, consumer, unescapedText, offsets, startOffset);
  }
}
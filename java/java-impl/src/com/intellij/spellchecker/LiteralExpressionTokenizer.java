// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author shkate@jetbrains.com
 */
public class LiteralExpressionTokenizer extends EscapeSequenceTokenizer<PsiLiteralExpression> {
  @Override
  public void tokenize(@NotNull PsiLiteralExpression expression, TokenConsumer consumer) {
    String text;
    if (!ExpressionUtils.hasStringType(expression)) {
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
    if (listOwner != null && AnnotationUtil.isAnnotated(listOwner, AnnotationUtil.NON_NLS, AnnotationUtil.CHECK_EXTERNAL)) {
      PsiElement targetElement = skipParenthesizedExprUp(getCompleteStringValueExpression(expression));
      if (listOwner instanceof PsiMethod) {
        if (Arrays.stream(PsiUtil.findReturnStatements(((PsiMethod)listOwner))).map(s -> s.getReturnValue()).anyMatch(e -> e == targetElement)) {
          return;
        }
      }
      else if (listOwner instanceof PsiVariable && ((PsiVariable)listOwner).getInitializer() == targetElement) {
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

  private static PsiElement skipParenthesizedExprUp(PsiElement expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = expression.getParent();
    }
    return expression;
  }

  public static void processTextWithEscapeSequences(PsiLiteralExpression element, String text, TokenConsumer consumer) {
    StringBuilder unescapedText = new StringBuilder();
    int[] offsets = new int[text.length() + 1];
    CodeInsightUtilCore.parseStringCharacters(text, unescapedText, offsets);

    int startOffset = (element != null && element.isTextBlock()) ? 3 : 1;
    processTextWithOffsets(element, consumer, unescapedText, offsets, startOffset);
  }

  public static PsiElement getCompleteStringValueExpression(PsiExpression expression) {
    return ExpressionUtils.isStringConcatenationOperand(expression) ? expression.getParent() : expression;
  }
}
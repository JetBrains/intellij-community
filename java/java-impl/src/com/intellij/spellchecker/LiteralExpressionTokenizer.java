// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author shkate@jetbrains.com
 */
public class LiteralExpressionTokenizer extends EscapeSequenceTokenizer<PsiLiteralExpression> {
  @Override
  public void tokenize(@NotNull PsiLiteralExpression element, TokenConsumer consumer) {
    PsiLiteralExpressionImpl literalExpression = (PsiLiteralExpressionImpl)element;
    if (literalExpression.getLiteralElementType() != JavaTokenType.STRING_LITERAL) return;  // not a string literal

    String text = literalExpression.getInnerText();
    if (StringUtil.isEmpty(text) || text.length() <= 2) { // optimisation to avoid expensive injection check
      return;
    }
    if (InjectedLanguageUtil.hasInjections(literalExpression)) return;

    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class);
    if (listOwner != null && AnnotationUtil.isAnnotated(listOwner, AnnotationUtil.NON_NLS, AnnotationUtil.CHECK_EXTERNAL)) {
      return;
    }

    if (!text.contains("\\")) {
      consumer.consumeToken(element, PlainTextSplitter.getInstance());
    }
    else {
      processTextWithEscapeSequences(element, text, consumer);
    }
  }

  public static void processTextWithEscapeSequences(PsiLiteralExpression element, String text, TokenConsumer consumer) {
    StringBuilder unescapedText = new StringBuilder();
    int[] offsets = new int[text.length()+1];
    PsiLiteralExpressionImpl.parseStringCharacters(text, unescapedText, offsets);

    processTextWithOffsets(element, consumer, unescapedText, offsets, 1);
  }
}
/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.util.Collections;

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
    if (listOwner != null && AnnotationUtil.isAnnotated(listOwner, Collections.singleton(AnnotationUtil.NON_NLS), false, false)) {
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

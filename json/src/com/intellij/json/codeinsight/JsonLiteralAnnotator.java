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
package com.intellij.json.codeinsight;

import com.intellij.json.JsonBundle;
import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.json.psi.JsonNumberLiteral;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonLiteralAnnotator implements Annotator {

  private static class Holder {
    private static final boolean DEBUG = ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    JsonLiteralChecker[] extensions = JsonLiteralChecker.EP_NAME.getExtensions();
    if (element instanceof JsonReferenceExpression) {
      highlightPropertyKey(element, holder);
    }
    else if (element instanceof JsonStringLiteral) {
      final JsonStringLiteral stringLiteral = (JsonStringLiteral)element;
      final int elementOffset = element.getTextOffset();
      highlightPropertyKey(element, holder);
      final String text = JsonPsiUtil.getElementTextWithoutHostEscaping(element);
      final int length = text.length();

      // Check that string literal is closed properly
      if (length <= 1 || text.charAt(0) != text.charAt(length - 1) || JsonPsiUtil.isEscapedChar(text, length - 1)) {
        holder.createErrorAnnotation(element, JsonBundle.message("syntax.error.missing.closing.quote"));
      }

      // Check escapes
      final List<Pair<TextRange, String>> fragments = stringLiteral.getTextFragments();
      for (Pair<TextRange, String> fragment: fragments) {
        for (JsonLiteralChecker checker: extensions) {
          if (!checker.isApplicable(element)) continue;
          String error = checker.getErrorForStringFragment(fragment.getSecond());
          if (error != null) {
            final TextRange fragmentRange = fragment.getFirst();
            holder.createErrorAnnotation(fragmentRange.shiftRight(elementOffset), error);
          }
        }
      }
    }
    else if (element instanceof JsonNumberLiteral) {
      String text = null;
      for (JsonLiteralChecker checker: extensions) {
        if (!checker.isApplicable(element)) continue;
        if (text == null) {
          text = JsonPsiUtil.getElementTextWithoutHostEscaping(element);
        }
        String error = checker.getErrorForNumericLiteral(text);
        if (error != null) {
          holder.createErrorAnnotation(element, error);
        }
      }
    }
  }

  private static void highlightPropertyKey(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (JsonPsiUtil.isPropertyKey(element)) {
      holder.createInfoAnnotation(element, Holder.DEBUG ? "property key" : null).setTextAttributes(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY);
    }
  }
}

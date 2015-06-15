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
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 */
public class JsonLiteralAnnotator implements Annotator {
  private static final Pattern VALID_ESCAPE = Pattern.compile("\\\\([\"\\\\/bfnrt]|u[0-9a-fA-F]{4})");
  private static final Pattern VALID_NUMBER_LITERAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

  private static class Holder {
    private static final boolean DEBUG = ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final String text = JsonPsiUtil.getElementTextWithoutHostEscaping(element);
    if (element instanceof JsonStringLiteral) {
      final JsonStringLiteral stringLiteral = (JsonStringLiteral)element;
      final int elementOffset = element.getTextOffset();
      if (JsonPsiUtil.isPropertyKey(element)) {
        holder.createInfoAnnotation(element, Holder.DEBUG ? "property key" : null).setTextAttributes(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY);
      }
      final int length = text.length();

      // Check that string literal is closed properly
      if (length <= 1 || text.charAt(0) != text.charAt(length - 1) || JsonPsiUtil.isEscapedChar(text, length - 1)) {
        holder.createErrorAnnotation(element, JsonBundle.message("syntax.error.missing.closing.quote"));
      }

      // Check escapes
      final List<Pair<TextRange, String>> fragments = stringLiteral.getTextFragments();
      for (Pair<TextRange, String> fragment : fragments) {
        final String fragmentText = fragment.getSecond();
        if (fragmentText.startsWith("\\") && fragmentText.length() > 1 && !VALID_ESCAPE.matcher(fragmentText).matches()) {
          final TextRange fragmentRange = fragment.getFirst();
          if (fragmentText.startsWith("\\u")) {
            holder.createErrorAnnotation(fragmentRange.shiftRight(elementOffset), JsonBundle.message(
              "syntax.error.illegal.unicode.escape.sequence"));
          }
          else {
            holder.createErrorAnnotation(fragmentRange.shiftRight(elementOffset), JsonBundle.message("syntax.error.illegal.escape.sequence"));
          }
        }
      }
    }
    else if (element instanceof JsonNumberLiteral) {
      if (!VALID_NUMBER_LITERAL.matcher(text).matches()) {
        holder.createErrorAnnotation(element, JsonBundle.message("syntax.error.illegal.floating.point.literal"));
      }
    }
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.json.JsonBundle;
import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.json.psi.JsonNumberLiteral;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class JsonLiteralAnnotator implements Annotator {

  private final boolean isDebug = ApplicationManager.getApplication().isUnitTestMode();

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    List<JsonLiteralChecker> extensions = JsonLiteralChecker.EP_NAME.getExtensionList();
    if (element instanceof JsonReferenceExpression) {
      highlightPropertyKey(element, holder);
    }
    else if (element instanceof JsonStringLiteral stringLiteral) {
      final int elementOffset = element.getTextOffset();
      highlightPropertyKey(element, holder);
      final String text = JsonPsiUtil.getElementTextWithoutHostEscaping(element);
      final int length = text.length();

      // Check that string literal is closed properly
      if (length <= 1 || text.charAt(0) != text.charAt(length - 1) || JsonPsiUtil.isEscapedChar(text, length - 1)) {
        holder.newAnnotation(HighlightSeverity.ERROR, JsonBundle.message("syntax.error.missing.closing.quote")).create();
      }

      // Check escapes
      final List<Pair<TextRange, String>> fragments = stringLiteral.getTextFragments();
      for (Pair<TextRange, String> fragment: fragments) {
        for (JsonLiteralChecker checker: extensions) {
          if (!checker.isApplicable(element)) continue;
          Pair<TextRange, @InspectionMessage String> error = checker.getErrorForStringFragment(fragment, stringLiteral);
          if (error != null) {
            holder.newAnnotation(HighlightSeverity.ERROR, error.second).range(error.getFirst().shiftRight(elementOffset)).create();
          }
        }
      }
    }
    else if (element instanceof JsonNumberLiteral) {
      String text = null;
      for (JsonLiteralChecker checker : extensions) {
        if (!checker.isApplicable(element)) continue;
        if (text == null) {
          text = JsonPsiUtil.getElementTextWithoutHostEscaping(element);
        }
        String error = checker.getErrorForNumericLiteral(text);
        if (error != null) {
          holder.newAnnotation(HighlightSeverity.ERROR, error).create();
        }
      }
    }
  }

  private void highlightPropertyKey(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (JsonPsiUtil.isPropertyKey(element)) {
      if (isDebug) {
        holder.newAnnotation(HighlightSeverity.INFORMATION, JsonBundle.message("annotation.property.key")).textAttributes(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY).create();
      }
      else {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY).create();
      }
    }
  }
}

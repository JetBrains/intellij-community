// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeEditor;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;

public final class JavaStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {

  @Override
  protected @NotNull PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
    return new JavaPsiBasedStripTrailingSpacesFilter(document);
  }

  @Override
  protected boolean isApplicableTo(@NotNull Language language) {
    return language.isKindOf(JavaLanguage.INSTANCE);
  }

  private static class JavaPsiBasedStripTrailingSpacesFilter extends PsiBasedStripTrailingSpacesFilter {

    protected JavaPsiBasedStripTrailingSpacesFilter(@NotNull Document document) {
      super(document);
    }

    @Override
    protected void process(@NotNull PsiFile psiFile) {
      if (!HighlightingFeature.TEXT_BLOCKS.isAvailable(psiFile)) return;
      psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
          if (expression.isTextBlock()) {
            disableRange(expression.getTextRange(), false);
          }
        }
      });
    }
  }
}

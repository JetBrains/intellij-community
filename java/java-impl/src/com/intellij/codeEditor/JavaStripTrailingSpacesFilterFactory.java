// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeEditor;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class JavaStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {

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
      if (!HighlightUtil.Feature.TEXT_BLOCKS.isAvailable(psiFile)) return;
      psiFile.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
          PsiLiteralExpressionImpl literal = ObjectUtils.tryCast(expression, PsiLiteralExpressionImpl.class);
          if (literal != null && literal.getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL) {
            disableRange(literal.getTextRange(), false);
          }
        }
      });
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaRainbowVisitor extends RainbowVisitor {
  @Override
  public boolean suitableForFile(@NotNull PsiFile psiFile) {
    return psiFile instanceof PsiJavaFile;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression
        || element instanceof PsiLocalVariable
        || element instanceof PsiParameter
        || element instanceof PsiDocParamRef) {
      PsiElement context = PsiTreeUtil.findFirstParent(element, p -> p instanceof PsiMethod || p instanceof PsiClassInitializer || p instanceof PsiLambdaExpression);
      if (context != null) {
        PsiElement rainbowElement = element instanceof PsiReferenceExpression || element instanceof PsiDocParamRef
                            ? element : ((PsiVariable)element).getNameIdentifier();
        PsiElement resolved = element instanceof PsiReferenceExpression
                             ? ((PsiReferenceExpression)element).resolve()
                             : element instanceof PsiDocParamRef
                               ? element.getReference() == null ? null : element.getReference().resolve()
                               : element;
        HighlightInfo attrs = getRainbowSymbolKey(context, rainbowElement, resolved);
        addInfo(attrs);
      }
    }
  }

  private @Nullable HighlightInfo getRainbowSymbolKey(@NotNull PsiElement context, PsiElement rainbowElement, PsiElement resolved) {
    if (rainbowElement == null || resolved == null) {
      return null;
    }
    if (PsiUtil.isJvmLocalVariable(resolved)) {
      String name = ((PsiVariable)resolved).getName();
      if (name != null) {
        return getInfo(context, rainbowElement, name, resolved instanceof PsiLocalVariable
                                                     ? JavaHighlightingColors.LOCAL_VARIABLE_ATTRIBUTES
                                                     : rainbowElement instanceof PsiDocTagValue
                                                       ? JavaHighlightingColors.DOC_COMMENT_TAG_VALUE
                                                       : JavaHighlightingColors.PARAMETER_ATTRIBUTES);
      }
    }
    return null;
  }

  @Override
  public @NotNull HighlightVisitor clone() {
    return new JavaRainbowVisitor();
  }
}


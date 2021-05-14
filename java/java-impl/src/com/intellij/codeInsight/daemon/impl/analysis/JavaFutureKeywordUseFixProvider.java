// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class JavaFutureKeywordUseFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    PsiTypeElement typeElement = ObjectUtils.tryCast(ref.getParent(), PsiTypeElement.class);
    if (typeElement == null || typeElement.getFirstChild() != typeElement.getLastChild()) return;
    PsiElement parent = typeElement.getParent();
    if (PsiKeyword.VAR.equals(ref.getReferenceName())) {
      registerVarLanguageLevelFix(ref, parent, registrar);
    }
    if (PsiKeyword.RECORD.equals(ref.getReferenceName())) {
      registerRecordLanguageLevelFix(ref, parent, registrar);
    }
  }

  private static void registerRecordLanguageLevelFix(@NotNull PsiJavaCodeReferenceElement ref,
                                                     PsiElement parent,
                                                     @NotNull QuickFixActionRegistrar registrar) {
    if ((parent instanceof PsiMethod || parent instanceof PsiField) && parent.getParent() instanceof PsiClass) {
      // record R() {} is parsed as method if records aren't supported
      // record R incomplete declaration is also possible
      HighlightUtil.registerIncreaseLanguageLevelFixes(ref, HighlightingFeature.RECORDS, registrar);
    }
    if (parent instanceof PsiLocalVariable && parent.getParent() instanceof PsiDeclarationStatement
        && ((PsiDeclarationStatement)parent.getParent()).getDeclaredElements().length == 1) {
      // record R() declaration inside method
      HighlightUtil.registerIncreaseLanguageLevelFixes(ref, HighlightingFeature.RECORDS, registrar);
    }
  }

  private static void registerVarLanguageLevelFix(@NotNull PsiJavaCodeReferenceElement ref,
                                                  PsiElement parent,
                                                  @NotNull QuickFixActionRegistrar registrar) {
    HighlightingFeature feature;
    if (parent instanceof PsiParameter && ((PsiParameter)parent).getDeclarationScope() instanceof PsiLambdaExpression) {
      feature = HighlightingFeature.VAR_LAMBDA_PARAMETER;
    }
    else {
      feature = HighlightingFeature.LVTI;
    }
    HighlightUtil.registerIncreaseLanguageLevelFixes(ref, feature, registrar);
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}

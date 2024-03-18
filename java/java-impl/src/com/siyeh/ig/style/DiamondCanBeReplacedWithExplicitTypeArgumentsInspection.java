// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.*;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DiamondCanBeReplacedWithExplicitTypeArgumentsInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DiamondTypeVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new DiamondTypeFix();
  }

  private static class DiamondTypeVisitor extends BaseInspectionVisitor {
    @Override
    public void visitReferenceParameterList(@NotNull PsiReferenceParameterList referenceParameterList) {
      super.visitReferenceParameterList(referenceParameterList);
      final PsiTypeElement[] typeParameterElements = referenceParameterList.getTypeParameterElements();
      if (typeParameterElements.length == 1) {
        final PsiTypeElement typeParameterElement = typeParameterElements[0];
        final PsiType type = typeParameterElement.getType();
        if (type instanceof PsiDiamondType) {
          final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(referenceParameterList, PsiNewExpression.class);
          if (newExpression != null) {
            final List<PsiType> types = PsiDiamondTypeImpl.resolveInferredTypesNoCheck(newExpression, newExpression).getInferredTypes();
            if (!types.isEmpty()) {
              boolean pullToErrors = !PsiUtil.isLanguageLevel7OrHigher(referenceParameterList) || 
                                     PsiDiamondTypeImpl.resolveInferredTypes(newExpression, newExpression).getErrorMessage() != null;
              registerError(referenceParameterList,
                            pullToErrors ? ProblemHighlightType.ERROR : ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
          }
        }
      }
    }
  }

  private static class DiamondTypeFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("diamond.can.be.replaced.with.explicit.type.arguments.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiDiamondTypeUtil.replaceDiamondWithExplicitTypes(startElement);
    }
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class LossyConversionCompoundAssignmentInspection extends BaseInspection {

  private static final Set<IElementType> SUPPORTED_SIGNS = Set.of(
    JavaTokenType.PLUSEQ,
    JavaTokenType.MINUSEQ,
    JavaTokenType.ASTERISKEQ,
    JavaTokenType.DIVEQ,
    JavaTokenType.ANDEQ,
    JavaTokenType.OREQ,
    JavaTokenType.XOREQ,
    JavaTokenType.PERCEQ
  );

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    PsiType lType = (PsiType)infos[1];
    PsiExpression rExpression = (PsiExpression)infos[2];
    return LocalQuickFix.from(new AddTypeCastFix(lType, rExpression));
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    PsiType rType = (PsiType)infos[0];
    PsiType lType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("inspection.lossy.conversion.compound.assignment.display.name",
                                           rType.getCanonicalText(), lType.getCanonicalText());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {

      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        PsiJavaToken sign = expression.getOperationSign();
        IElementType tokenType = sign.getTokenType();
        if (tokenType == null || !SUPPORTED_SIGNS.contains(tokenType)) {
          return;
        }
        PsiExpression rExpression = expression.getRExpression();
        PsiExpression lExpression = expression.getLExpression();
        if (rExpression == null) {
          return;
        }
        PsiType lType = lExpression.getType();
        PsiType rType = rExpression.getType();
        if (lType == null || rType == null) {
          return;
        }
        if (!(TypeConversionUtil.isPrimitiveAndNotNull(lType) &&
              TypeConversionUtil.isNumericType(lType) &&
              TypeConversionUtil.isNumericType(rType)
        )) {
          return;
        }

        if (TypeConversionUtil.areTypesAssignmentCompatible(lType, rExpression) || !TypeConversionUtil.areTypesConvertible(rType, lType)) {
          return;
        }
        registerError(rExpression, rType, lType, rExpression);
      }
    };
  }
}
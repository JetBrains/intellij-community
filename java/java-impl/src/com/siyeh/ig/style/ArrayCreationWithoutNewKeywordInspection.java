// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ArrayCreationWithoutNewKeywordInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayCreationExpressionVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof String) {
      return new ArrayCreationExpressionFix((String)infos[0]);
    }
    return null;
  }

  private static class ArrayCreationExpressionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiType type = expression.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        final String typeText = TypeConversionUtil.erasure(type).getCanonicalText();
        registerError(expression, typeText);
      }
    }
  }

  private static class ArrayCreationExpressionFix extends PsiUpdateModCommandQuickFix {
    private final String myType;

    ArrayCreationExpressionFix(String type) {
      myType = type;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("array.creation.without.new.keyword.quickfix", myType);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("array.creation.without.new.keyword.family.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiArrayInitializerExpression arrayInitializerExpression) {
        final PsiType type = arrayInitializerExpression.getType();
        if (type != null) {
          PsiReplacementUtil.replaceExpression(arrayInitializerExpression, "new " +
                                                                           TypeConversionUtil.erasure(type).getCanonicalText() +
                                                                           arrayInitializerExpression.getText());
        }
      }
    }
  }
}

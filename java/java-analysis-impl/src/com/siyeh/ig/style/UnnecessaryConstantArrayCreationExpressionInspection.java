// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnnecessaryConstantArrayCreationExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.constant.array.creation.expression.problem.descriptor");
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    if (infos.length != 0 && infos[0] instanceof String) {
      return new UnnecessaryConstantArrayCreationExpressionFix((String)infos[0]);
    }
    return null;
  }

  private static final class UnnecessaryConstantArrayCreationExpressionFix extends PsiUpdateModCommandQuickFix {
    private final String myType;

    private UnnecessaryConstantArrayCreationExpressionFix(String type) {
      myType = type;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.constant.array.creation.expression.family.quickfix");
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.remove", "new " + myType);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiNewExpression newExpression)) {
        return;
      }
      final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
      if (arrayInitializer == null) {
        return;
      }
      PsiExpression target = newExpression;
      while(target.getParent() instanceof PsiParenthesizedExpression) {
        target = (PsiExpression)target.getParent();
      }
      new CommentTracker().replaceAndRestoreComments(target, arrayInitializer);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConstantArrayCreationExpressionVisitor();
  }

  private static class UnnecessaryConstantArrayCreationExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiNewExpression)) {
        return;
      }
      final PsiElement grandParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
      if (!(grandParent instanceof PsiVariable variable)) {
        return;
      }
      final PsiType expressionType = expression.getType();
      if (!variable.getType().equals(expressionType)) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null && typeElement.isInferredType()) {
        return;
      }
      if (hasGenericTypeParameters(variable)) {
        return;
      }
      registerErrorAtOffset(parent, 0, expression.getStartOffsetInParent(),
                            expressionType.getPresentableText());
    }

    private static boolean hasGenericTypeParameters(PsiVariable variable) {
      final PsiType type = variable.getType();
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType classType)) {
        return false;
      }
      final PsiType[] parameterTypes = classType.getParameters();
      for (PsiType parameterType : parameterTypes) {
        if (parameterType != null) {
          return true;
        }
      }
      return false;
    }
  }
}
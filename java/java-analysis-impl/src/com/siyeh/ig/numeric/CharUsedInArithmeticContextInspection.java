// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CharUsedInArithmeticContextInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("char.used.in.arithmetic.context.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> result = new ArrayList<>();
    final PsiElement expression = (PsiElement)infos[0];
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiExpression binaryExpression) {
      final PsiType type = binaryExpression.getType();
      if (type instanceof PsiPrimitiveType && !type.equals(PsiTypes.charType())) {
        final String typeText = type.getCanonicalText();
        result.add(new CharUsedInArithmeticContentCastFix(typeText));
      }
    }
    if (!(expression instanceof PsiLiteralExpression) &&
        !(expression instanceof PsiParenthesizedExpression &&
          PsiUtil.skipParenthesizedExprDown((PsiExpression)expression) instanceof PsiLiteralExpression)) {
      return result.toArray(LocalQuickFix.EMPTY_ARRAY);
    }
    while (parent instanceof PsiPolyadicExpression) {
      if (ExpressionUtils.hasStringType((PsiExpression)parent)) {
        result.add(new CharUsedInArithmeticContentFix());
        break;
      }
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }

    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static class CharUsedInArithmeticContentFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("char.used.in.arithmetic.context.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiLiteralExpression literalExpression)) {
        return;
      }
      final Object literal = literalExpression.getValue();
      if (!(literal instanceof Character)) {
        return;
      }
      final String escaped = StringUtil.escapeStringCharacters(literal.toString());
      PsiReplacementUtil.replaceExpression(literalExpression, '\"' + escaped + '"');
    }
  }

  private static class CharUsedInArithmeticContentCastFix extends PsiUpdateModCommandQuickFix {

    private final String typeText;

    CharUsedInArithmeticContentCastFix(String typeText) {
      this.typeText = typeText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("char.used.in.arithmetic.context.cast.quickfix", typeText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("char.used.in.arithmetic.content.cast.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiExpression expression)) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      final String expressionText = commentTracker.text(expression);
      PsiReplacementUtil.replaceExpression(expression, '(' + typeText + ')' + expressionText, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CharUsedInArithmeticContextVisitor();
  }

  private static class CharUsedInArithmeticContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (ComparisonUtils.isComparisonOperation(tokenType)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      PsiType leftType = operands[0].getType();
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        final PsiType rightType = operand.getType();
        final PsiType expressionType = TypeConversionUtil.calcTypeForBinaryExpression(leftType, rightType, tokenType, true);
        if (TypeUtils.isJavaLangString(expressionType)) {
          return;
        }
        if (PsiTypes.charType().equals(rightType)) {
          registerError(operand, operand);
        }
        if (PsiTypes.charType().equals(leftType) && i == 1) {
          registerError(operands[0], operands[0]);
        }
        leftType = rightType;
      }
    }
  }
}

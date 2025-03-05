// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class CastedLiteralMaybeJustLiteralInspection extends BaseInspection {

  @Override
  protected final @NotNull String buildErrorString(Object... infos) {
    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText = buildReplacementText(typeCastExpression, new StringBuilder());
    return InspectionGadgetsBundle.message("int.literal.may.be.long.literal.problem.descriptor", replacementText);
  }

  abstract @NotNull String getSuffix();

  abstract @NotNull PsiType getTypeBeforeCast();

  abstract @NotNull PsiPrimitiveType getCastType();

  private StringBuilder buildReplacementText(PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiLiteralExpression) {
      out.append(expression.getText()).append(getSuffix());
    }
    else if (expression instanceof PsiPrefixExpression prefixExpression) {
      out.append(prefixExpression.getOperationSign().getText());
      return buildReplacementText(prefixExpression.getOperand(), out);
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      out.append('(');
      buildReplacementText(parenthesizedExpression.getExpression(), out);
      out.append(')');
    }
    else if (expression instanceof PsiTypeCastExpression typeCastExpression) {
      buildReplacementText(typeCastExpression.getOperand(), out);
    }
    else {
      assert false;
    }
    return out;
  }

  @Override
  protected final LocalQuickFix buildFix(Object... infos) {
    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText = buildReplacementText(typeCastExpression, new StringBuilder());
    return new ReplaceCastedLiteralWithJustLiteralFix(replacementText.toString());
  }

  private class ReplaceCastedLiteralWithJustLiteralFix extends PsiUpdateModCommandQuickFix {

    private final String replacementString;

    ReplaceCastedLiteralWithJustLiteralFix(String replacementString) {
      this.replacementString = replacementString;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", replacementString);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle
        .message("replace.casted.literal.with.just.literal.fix.family.name", getCastType().getPresentableText());
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiTypeCastExpression typeCastExpression)) {
        return;
      }
      PsiReplacementUtil.replaceExpression(typeCastExpression, replacementString);
    }
  }

  @Override
  public final BaseInspectionVisitor buildVisitor() {
    return new CastedLiteralMayBeJustLiteralVisitor();
  }

  private class CastedLiteralMayBeJustLiteralVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!getTypeBeforeCast().equals(type)) {
        return;
      }
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiPrefixExpression || parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiTypeCastExpression typeCastExpression)) {
        return;
      }
      final PsiType castType = typeCastExpression.getType();
      if (!getCastType().equals(castType)) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(typeCastExpression, false);
      // don't warn on red code.
      if (expectedType == null) {
        return;
      }
      else if (!getCastType().equals(expectedType)) {
        final PsiClassType boxedType = getCastType().getBoxedType(expression);
        assert boxedType != null;
        if (!boxedType.equals(expectedType)) {
          return;
        }
      }
      registerError(typeCastExpression, typeCastExpression);
    }
  }
}

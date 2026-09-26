// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
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
    PsiExpression lExpression = (PsiExpression)infos[2];
    PsiExpression rExpression = (PsiExpression)infos[3];
    if (isSimpleRhs(rExpression, lExpression)) {
      return LocalQuickFix.from(new AddTypeCastFix(lType, rExpression));
    }
    if (SideEffectChecker.mayHaveSideEffects(lExpression)) {
      return null;
    }
    return new ExpandAndCastFix(lType.getCanonicalText());
  }

  private static boolean isSimpleRhs(@Nullable PsiExpression rhs, @Nullable PsiExpression lhs) {
    if (lhs == null) return false;
    return switch (PsiUtil.skipParenthesizedExprDown(rhs)) {
      case PsiLiteralExpression _, PsiReferenceExpression _ -> JavaPsiPatternUtil.isUnconditionalConversion(rhs, lhs.getType());
      case PsiUnaryExpression unary -> isSimpleRhs(unary.getOperand(), lhs);
      case null, default -> false;
    };
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    PsiType rType = (PsiType)infos[0];
    PsiType lType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("inspection.lossy.conversion.compound.assignment.display.name",
                                           rType.getCanonicalText(), lType.getCanonicalText());
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
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
        registerError(rExpression, rType, lType, lExpression, rExpression);
      }
    };
  }

  private static final class ExpandAndCastFix extends PsiUpdateModCommandQuickFix {
    private final String myTypeText;

    ExpandAndCastFix(@NotNull String typeText) {
      myTypeText = typeText;
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return InspectionGadgetsBundle.message(
        "inspection.lossy.conversion.compound.assignment.expand.fix.name", myTypeText);
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.lossy.conversion.compound.assignment.expand.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, false);
      if (assignment == null) return;
      PsiJavaToken sign = assignment.getOperationSign();
      if (JavaTokenType.EQ.equals(sign.getTokenType())) return;
      PsiExpression lhs = assignment.getLExpression();
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) return;
      CommentTracker ct = new CommentTracker();
      String op = StringUtil.trimEnd(sign.getText(), "=");
      String lhsText = ct.text(lhs);
      String rhsText = PsiPrecedenceUtil.areParenthesesNeeded(sign, rhs)
                       ? '(' + ct.text(rhs) + ')'
                       : ct.text(rhs);
      String newText = lhsText + " = (" + myTypeText + ") (" + lhsText + ' ' + op + ' ' + rhsText + ')';
      PsiReplacementUtil.replaceExpression(assignment, newText, ct);
    }
  }
}

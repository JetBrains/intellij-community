// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class UnnecessaryExplicitNumericCastInspection extends BaseInspection implements CleanupLocalInspectionTool {
  private static final TokenSet binaryPromotionOperators = TokenSet.create(
    JavaTokenType.ASTERISK,
    JavaTokenType.DIV,
    JavaTokenType.PERC,
    JavaTokenType.PLUS,
    JavaTokenType.MINUS,
    JavaTokenType.LT,
    JavaTokenType.LE,
    JavaTokenType.GT,
    JavaTokenType.GE,
    JavaTokenType.EQEQ,
    JavaTokenType.NE,
    JavaTokenType.AND,
    JavaTokenType.XOR,
    JavaTokenType.OR
  );

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return InspectionGadgetsBundle.message("unnecessary.explicit.numeric.cast.problem.descriptor", expression.getText());
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryExplicitNumericCastFix();
  }

  private static class UnnecessaryExplicitNumericCastFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.explicit.numeric.cast.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiTypeCastExpression typeCastExpression)) {
        return;
      }
      if (!isUnnecessaryPrimitiveNumericCast(typeCastExpression)) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      while (grandParent instanceof PsiParenthesizedExpression) {
        parent = grandParent;
        grandParent = parent.getParent();
      }
      final PsiExpression operand = typeCastExpression.getOperand();
      if (operand == null) {
        parent.delete();
      }
      else {
        parent.replace(operand);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryExplicitNumericCastVisitor();
  }

  private static class UnnecessaryExplicitNumericCastVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      if (!isUnnecessaryPrimitiveNumericCast(expression)) {
        // equal types is caught by "Redundant type cast" inspection
        return;
      }
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement != null) {
        registerError(typeElement, expression.getOperand());
      }
    }
  }

  public static boolean isUnnecessaryPrimitiveNumericCast(PsiTypeCastExpression expression) {
    final PsiType castType = expression.getType();
    if (!ClassUtils.isPrimitiveNumericType(castType)) {
      return false;
    }
    final PsiExpression operand = expression.getOperand();
    if (operand == null) {
      return false;
    }
    final PsiType operandType = operand.getType();
    if (!ClassUtils.isPrimitiveNumericType(operandType)) {
      return false;
    }
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiPrefixExpression prefixExpression) {
      // JLS 5.6 Numeric Contexts
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (JavaTokenType.MINUS == tokenType || JavaTokenType.PLUS == tokenType || JavaTokenType.TILDE == tokenType) {
        if (TypeUtils.isNarrowingConversion(operandType, castType)) {
          return false;
        }
        if (PsiTypes.intType().equals(castType)) {
          return !PsiTypes.longType().equals(operandType) && !PsiTypes.floatType().equals(operandType) && !PsiTypes.doubleType().equals(operandType);
        }
      }
      return false;
    }
    if (castType.equals(operandType)) {
      // cast to the same type is caught by "Redundant type cast" inspection
      return false;
    }
    if (parent instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (binaryPromotionOperators.contains(tokenType)) {
        if (TypeUtils.isNarrowingConversion(operandType, castType)) {
          return false;
        }
        if (PsiTypes.intType().equals(castType)) {
          if (PsiTypes.charType().equals(operandType) && TypeUtils.getStringType(polyadicExpression).equals(polyadicExpression.getType())) {
            return false;
          }
          return !PsiTypes.longType().equals(operandType) && !PsiTypes.floatType().equals(operandType) && !PsiTypes.doubleType().equals(operandType);
        }
        if (PsiTypes.longType().equals(castType) || PsiTypes.floatType().equals(castType) || PsiTypes.doubleType().equals(castType)) {
          final PsiExpression[] operands = polyadicExpression.getOperands();
          int expressionIndex = -1;
          for (int i = 0; i < operands.length; i++) {
            if (expressionIndex == 0 && i > 1) {
              return false;
            }
            final PsiExpression operand1 = operands[i];
            if (PsiTreeUtil.isAncestor(operand1, expression, false)) {
              if (i > 0) {
                return false;
              }
              else {
                expressionIndex = i;
                continue;
              }
            }
            final PsiType type = operand1.getType();
            if (castType.equals(type)) {
              return true;
            }
          }
        }
      }
      else if (JavaBinaryOperations.SHIFT_OPS.contains(tokenType)) {
        final PsiExpression firstOperand = polyadicExpression.getOperands()[0];
        if (!PsiTreeUtil.isAncestor(firstOperand, expression, false)) {
          return true;
        }
        return !PsiTypes.longType().equals(castType) && isLegalWideningConversion(operand, PsiTypes.intType());
      }
      return false;
    }
    else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      final PsiType lhsType = assignmentExpression.getType();
      if (castType.equals(lhsType) && (isLegalAssignmentConversion(operand, lhsType) || isLegalWideningConversion(operand, lhsType))) return true;
    }
    else if (parent instanceof PsiVariable variable) {
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null || typeElement.isInferredType()) {
        return false;
      }
      final PsiType lhsType = variable.getType();
      if (castType.equals(lhsType) && (isLegalAssignmentConversion(operand, lhsType) || isLegalWideningConversion(operand, lhsType))) return true;
    }
    else if (MethodCallUtils.isNecessaryForSurroundingMethodCall(expression, operand)) {
      return false;
    }
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
    return operandType.equals(expectedType) && isLegalAssignmentConversion(operand, castType) ||
           castType.equals(expectedType) && isLegalWideningConversion(operand, castType);
  }

  static boolean isLegalWideningConversion(PsiExpression expression, PsiType requiredType) {
    final PsiType operandType = expression.getType();
    if (PsiTypes.doubleType().equals(requiredType)) {
      return PsiTypes.floatType().equals(operandType) ||
             PsiTypes.longType().equals(operandType) ||
             PsiTypes.intType().equals(operandType) ||
             PsiTypes.charType().equals(operandType) ||
             PsiTypes.shortType().equals(operandType) ||
             PsiTypes.byteType().equals(operandType);
    }
    else if (PsiTypes.floatType().equals(requiredType)) {
      return PsiTypes.longType().equals(operandType) ||
             PsiTypes.intType().equals(operandType) ||
             PsiTypes.charType().equals(operandType) ||
             PsiTypes.shortType().equals(operandType) ||
             PsiTypes.byteType().equals(operandType);
    }
    else if (PsiTypes.longType().equals(requiredType)) {
      return PsiTypes.intType().equals(operandType) ||
             PsiTypes.charType().equals(operandType) ||
             PsiTypes.shortType().equals(operandType) ||
             PsiTypes.byteType().equals(operandType);
    }
    else if (PsiTypes.intType().equals(requiredType)) {
      return PsiTypes.charType().equals(operandType) ||
             PsiTypes.shortType().equals(operandType) ||
             PsiTypes.byteType().equals(operandType);
    }
    return false;
  }

  static boolean isLegalAssignmentConversion(PsiExpression expression, PsiType assignmentType) {
    // JLS 5.2 Assignment Conversion
    if (PsiTypes.shortType().equals(assignmentType)) {
      return canValueBeContained(expression, Short.MIN_VALUE, Short.MAX_VALUE);
    }
    else if (PsiTypes.charType().equals(assignmentType)) {
      return canValueBeContained(expression, Character.MIN_VALUE, Character.MAX_VALUE);
    }
    else if (PsiTypes.byteType().equals(assignmentType)) {
      return canValueBeContained(expression, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
    return false;
  }

  private static boolean canValueBeContained(PsiExpression expression, int lowerBound, int upperBound) {
    final PsiType expressionType = expression.getType();
    if (!PsiTypes.intType().equals(expressionType)) {
      return false;
    }
    final Object constant = ExpressionUtils.computeConstantExpression(expression);
    if (!(constant instanceof Integer)) {
      return false;
    }
    final int i = ((Integer)constant).intValue();
    return i >= lowerBound && i <= upperBound;
  }
}

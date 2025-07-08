// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.OrderedBinaryExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

final class EqualsChecker {
  private static final CallMatcher GET_CLASS = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);
  private static final CallMatcher OBJECTS_EQUALS = CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "equals")
    .parameterCount(2);
  private static final CallMatcher BITS =
    CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "doubleToLongBits", "doubleToRawLongBits"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "floatToIntBits", "floatToRawIntBits")
    );


  /**
   * Detects standard equals method implementation with identity check, null/class check, and field-by-field comparison, in the form of:
   * <pre>
   *   public boolean equals(Object obj) {
   *     if (obj == this) return true;
   *     if (obj == null || obj.getClass() != this.getClass()) return false;
   *     var that = (Person)obj;
   *     return Objects.equals(this.name, that.name) &&
   *     this.age == that.age;
   *   }
   * </pre>
   */
  static boolean isStandardEqualsMethod(@Nullable PsiMethod method, @NotNull Set<PsiField> fields) {
    if (method == null) return false;
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;
    PsiParameter parameter = method.getParameterList().getParameter(0);
    PsiStatement[] statements = body.getStatements();
    int shift;
    if (statements.length == 4) {
      if (!isThisCheck(statements[0], parameter)) return false;
      shift = 1;
    }
    else {
      if (statements.length != 3) return false;
      shift = 0;
    }
    if (!isNullClassCheck(statements[shift], parameter)) return false;
    PsiVariable that = extractThatVariable(statements[1 + shift], parameter);
    if (that == null) return false;
    if (!(statements[2 + shift] instanceof PsiReturnStatement returnStatement)) return false;
    if (!(PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue()) instanceof PsiPolyadicExpression poly)) return false;
    if (!poly.getOperationTokenType().equals(JavaTokenType.ANDAND)) return false;
    PsiExpression[] operands = poly.getOperands();
    if (fields.size() != operands.length) return false;
    Set<PsiField> comparedFields = new HashSet<>();
    for (PsiExpression operand : operands) {
      PsiField field = getComparedField(operand, that);
      if (field == null || !comparedFields.add(field)) return false;
    }
    return comparedFields.equals(fields);
  }

  /**
   * Matches {@code Objects.equals(this.x, that.x)} or {@code this.x == that.x} or
   * {@code Double.doubleToLongBits(this.x) == Double.doubleToLongBits(that.x)}
   * and returns the field x; null if not matched
   */
  private static @Nullable PsiField getComparedField(PsiExpression operand, PsiVariable that) {
    operand = PsiUtil.skipParenthesizedExprDown(operand);
    if (operand instanceof PsiMethodCallExpression call) {
      if (OBJECTS_EQUALS.test(call)) {
        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        return getField(expressions[0], expressions[1], that);
      }
    }
    if (operand instanceof PsiBinaryExpression binOp && binOp.getOperationTokenType().equals(JavaTokenType.EQEQ)) {
      PsiExpression lOperand = binOp.getLOperand();
      PsiExpression rOperand = binOp.getROperand();
      PsiField field = getField(lOperand, rOperand, that);
      if (field != null) {
        PsiType type = field.getType();
        if (type instanceof PsiPrimitiveType && !TypeConversionUtil.isFloatOrDoubleType(type)) return field;
      }
      if (lOperand instanceof PsiMethodCallExpression lCall &&
          rOperand instanceof PsiMethodCallExpression rCall &&
          BITS.test(lCall) && BITS.test(rCall)) {
        field = getField(lCall.getArgumentList().getExpressions()[0], rCall.getArgumentList().getExpressions()[0], that);
        if (field != null) {
          PsiType type = field.getType();
          if (type instanceof PsiPrimitiveType && TypeConversionUtil.isFloatOrDoubleType(type)) return field;
        }
      }
    }
    return null;
  }

  /**
   * Ensures that left is this.x and right is that.x (or vice versa) where x is the same field;
   * returns that field if matched
   */
  private static @Nullable PsiField getField(PsiExpression left, PsiExpression right, PsiVariable that) {
    if (!(PsiUtil.skipParenthesizedExprDown(left) instanceof PsiReferenceExpression leftRef)) return null;
    if (!(PsiUtil.skipParenthesizedExprDown(right) instanceof PsiReferenceExpression rightRef)) return null;
    if (!(leftRef.resolve() instanceof PsiField leftField)) return null;
    if (!(rightRef.resolve() instanceof PsiField rightField)) return null;
    if (!leftField.isEquivalentTo(rightField)) return null;
    PsiExpression leftQualifier = leftRef.getQualifierExpression();
    PsiExpression rightQualifier = rightRef.getQualifierExpression();
    if (ExpressionUtils.isReferenceTo(leftQualifier, that) &&
        (rightQualifier == null || rightQualifier instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null)) {
      return leftField;
    }
    if (ExpressionUtils.isReferenceTo(rightQualifier, that) &&
        (leftQualifier == null || leftQualifier instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null)) {
      return leftField;
    }
    return null;
  }

  /**
   * Matches variable declaration like {@code var that = (Cls)parameter} and returns the new variable
   */
  private static PsiVariable extractThatVariable(PsiStatement statement, PsiParameter parameter) {
    if (!(statement instanceof PsiDeclarationStatement decl)) return null;
    PsiElement[] elements = decl.getDeclaredElements();
    if (elements.length != 1 || !(elements[0] instanceof PsiLocalVariable var)) return null;
    if (!(PsiUtil.skipParenthesizedExprDown(var.getInitializer()) instanceof PsiTypeCastExpression cast)) return null;
    if (!ExpressionUtils.isReferenceTo(cast.getOperand(), parameter)) return null;
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(var.getType());
    if (psiClass == null || !psiClass.isEquivalentTo(PsiTreeUtil.getParentOfType(statement, PsiClass.class))) return null;
    return var;
  }

  /**
   * @return true if the statement is {@code if(this == parameter) return true;} check
   */
  private static boolean isThisCheck(PsiStatement statement, PsiParameter parameter) {
    if (!(statement instanceof PsiIfStatement ifStatement)) return false;
    if (ifStatement.getElseBranch() != null) return false;
    if (!returnsBoolean(ifStatement.getThenBranch(), true)) return false;
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    OrderedBinaryExpression<PsiThisExpression, PsiExpression> expr = OrderedBinaryExpression.from(condition, PsiThisExpression.class);
    return expr != null && expr.getFirstOperand().getQualifier() == null &&
           expr.getTokenType() == JavaTokenType.EQEQ &&
           ExpressionUtils.isReferenceTo(expr.getSecondOperand(), parameter);
  }

  /**
   * @return true if the statement is {@code if(parameter == null || parameter.getClass() != this.getClass()) return false;} check
   */
  private static boolean isNullClassCheck(PsiStatement statement, PsiParameter parameter) {
    if (!(statement instanceof PsiIfStatement ifStatement)) return false;
    if (ifStatement.getElseBranch() != null) return false;
    if (!returnsBoolean(ifStatement.getThenBranch(), false)) return false;
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (!(condition instanceof PsiBinaryExpression disjunction) || !disjunction.getOperationTokenType().equals(JavaTokenType.OROR)) {
      return false;
    }
    if (ExpressionUtils.getVariableFromNullComparison(disjunction.getLOperand(), true) != parameter) return false;
    PsiExpression rOperand = disjunction.getROperand();
    if (rOperand instanceof PsiBinaryExpression rightBinOp) {
      if (!rightBinOp.getOperationTokenType().equals(JavaTokenType.NE)) return false;
      PsiExpression left = rightBinOp.getLOperand();
      PsiExpression right = rightBinOp.getROperand();
      return isObjectGetClass(left, parameter) && isThisClass(right) ||
             isObjectGetClass(right, parameter) && isThisClass(left);
    }
    if (BoolUtils.getNegated(rOperand) instanceof PsiInstanceOfExpression instanceOfExpression) {
      return ExpressionUtils.isReferenceTo(instanceOfExpression.getOperand(), parameter) &&
             isCurrentType(instanceOfExpression.getCheckType());
    }
    return false;
  }

  /**
   * @return true if expression is either {@code this.getClass()} or an explicit class literal for the containing class
   */
  private static boolean isThisClass(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression call) {
      if (!GET_CLASS.test(call)) return false;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      return qualifier == null || qualifier instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null;
    }
    if (expression instanceof PsiClassObjectAccessExpression classExpression) {
      return isCurrentType(classExpression.getOperand());
    }
    return false;
  }

  private static boolean isCurrentType(PsiTypeElement operand) {
    if (operand == null) return false;
    PsiType type = operand.getType();
    PsiClass containingClass = PsiTreeUtil.getParentOfType(operand, PsiClass.class);
    return containingClass != null && containingClass.isEquivalentTo(PsiUtil.resolveClassInClassTypeOnly(type));
  }

  private static boolean isObjectGetClass(PsiExpression expression, PsiParameter parameter) {
    return PsiUtil.skipParenthesizedExprDown(expression) instanceof PsiMethodCallExpression call &&
           GET_CLASS.test(call) && ExpressionUtils.isReferenceTo(call.getMethodExpression().getQualifierExpression(), parameter);
  }

  private static boolean returnsBoolean(PsiStatement statement, boolean expectedValue) {
    return ControlFlowUtils.stripBraces(statement) instanceof PsiReturnStatement returnStatement &&
           returnStatement.getReturnValue() instanceof PsiLiteralExpression literal &&
           literal.getValue() instanceof Boolean value &&
           value.equals(expectedValue);
  }
}

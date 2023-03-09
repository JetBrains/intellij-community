// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

final class ConstantExpressionVisitor extends JavaElementVisitor implements PsiConstantEvaluationHelper.AuxEvaluator {
  private final Interner<String> myInterner = Interner.createStringInterner();

  private Set<PsiVariable> myVisitedVars;
  private final Map<PsiElement, Object> myCachedValues = new HashMap<>();
  private final boolean myThrowExceptionOnOverflow;

  private Object myResult;

  private final PsiConstantEvaluationHelper.AuxEvaluator myAuxEvaluator;

  ConstantExpressionVisitor(Set<PsiVariable> visitedVars, boolean throwExceptionOnOverflow, final PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    myVisitedVars = visitedVars;
    myThrowExceptionOnOverflow = throwExceptionOnOverflow;
    myAuxEvaluator = auxEvaluator;
  }

  Object handle(PsiElement element) {
    myResult = null;
    element.accept(this);
    store(element, myResult);
    return myResult;
  }

  private Object getStoredValue(PsiElement element) {
    return myCachedValues.remove(element);
  }

  void store(PsiElement element, Object value) {
    myCachedValues.put(element, value);
  }

  @Override
  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    final Object value = expression.getValue();
    myResult = value instanceof String ? myInterner.intern((String)value) : value;
  }

  @Override
  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
    final PsiTypeElement castTypeElement = expression.getCastType();

    PsiExpression operand = expression.getOperand();
    Object opValue = getStoredValue(operand);
    if(castTypeElement == null || opValue == null) {
      myResult = null;
      return;
    }

    PsiType castType = castTypeElement.getType();
    // According to JLS 15.28 Constant Expressions, only casts to primitive types and to String type are considered constant
    if (!(castType instanceof PsiPrimitiveType) && !castType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      myResult = null;
      return;
    }
    myResult = ConstantExpressionUtil.computeCastTo(opValue, castType);
  }

  @Override public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
    Object then = getStoredValue(expression.getThenExpression());
    Object els = getStoredValue(expression.getElseExpression());
    Object condition = getStoredValue(expression.getCondition());
    if (then == null || els == null) {
      myResult = null;
      return;
    }

    Object value = null;

    if (condition instanceof Boolean) {
      value = ((Boolean)condition).booleanValue() ? then : els;
    }

    myResult = value;
  }

  @Override
  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
    PsiExpression[] operands = expression.getOperands();
    Object lValue = getStoredValue(operands[0]);
    if (lValue == null) {
      myResult = null;
      return;
    }
    IElementType tokenType = expression.getOperationTokenType();
    for (int i = 1; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      Object rValue = getStoredValue(operand);
      if (rValue == null) {
        myResult = null;
        break;
      }
      myResult = compute(lValue, rValue, tokenType, expression);
      if (myResult == null) {
        break;
      }
      lValue = myResult;
    }
    if (myResult instanceof String) {
      myResult = myInterner.intern((String)myResult);
    }
  }

  private Object compute(Object lOperandValue, Object rOperandValue, IElementType tokenType, PsiPolyadicExpression expression) {
    Object value = null;
    if (tokenType == JavaTokenType.PLUS) {
      if (lOperandValue instanceof String || rOperandValue instanceof String) {
        String l = computeValueToString(lOperandValue);
        String r = computeValueToString(rOperandValue);
        value = l + r;
      }
      else {
        if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
        if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());

        if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
          if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
            value = Double.valueOf(((Number)lOperandValue).doubleValue() + ((Number)rOperandValue).doubleValue());
            checkRealNumberOverflow(value, lOperandValue, rOperandValue,expression);
          }
          else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
            value = Float.valueOf(((Number)lOperandValue).floatValue() + ((Number)rOperandValue).floatValue());
            checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
          }
          else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
            final long l = ((Number)lOperandValue).longValue();
            final long r = ((Number)rOperandValue).longValue();
            value = Long.valueOf(l + r);
            checkAdditionOverflow(((Long)value).longValue() >= 0, l >= 0, r >= 0, expression);
          }
          else {
            final int l = ((Number)lOperandValue).intValue();
            final int r = ((Number)rOperandValue).intValue();
            value = Integer.valueOf(l + r);
            checkAdditionOverflow(((Integer)value).intValue() >= 0, l >= 0, r >= 0, expression);
          }
        }
      }
    }
    else if (tokenType == JavaTokenType.MINUS) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = Double.valueOf(((Number)lOperandValue).doubleValue() - ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = Float.valueOf(((Number)lOperandValue).floatValue() - ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          final long l = ((Number)lOperandValue).longValue();
          final long r = ((Number)rOperandValue).longValue();
          value = Long.valueOf(l - r);
          checkAdditionOverflow(((Long)value).longValue() >= 0, l >= 0, r < 0, expression);
        }
        else {
          final int l = ((Number)lOperandValue).intValue();
          final int r = ((Number)rOperandValue).intValue();
          value = Integer.valueOf(l - r);
          checkAdditionOverflow(((Integer)value).intValue() >= 0, l >= 0, r < 0, expression);
        }
      }
    }
    else if (tokenType == JavaTokenType.ANDAND) {
      if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() && ((Boolean)rOperandValue).booleanValue());
      }
      else if (lOperandValue instanceof Boolean && !((Boolean)lOperandValue).booleanValue() ||
               rOperandValue instanceof Boolean && !((Boolean)rOperandValue).booleanValue()) {
        value = Boolean.FALSE;
      }
    }
    else if (tokenType == JavaTokenType.OROR) {
      if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() || ((Boolean)rOperandValue).booleanValue());
      }
      else if (lOperandValue instanceof Boolean && ((Boolean)lOperandValue).booleanValue() ||
               rOperandValue instanceof Boolean && ((Boolean)rOperandValue).booleanValue()) {
        value = Boolean.TRUE;
      }
    }
    else if (tokenType == JavaTokenType.LT || tokenType == JavaTokenType.LE ||
             tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE) {
      value = compareNumbers(lOperandValue, rOperandValue, tokenType);
    }
    else if (tokenType == JavaTokenType.EQEQ || tokenType == JavaTokenType.NE) {
      value = handleEqualityComparison(lOperandValue, rOperandValue, tokenType);
    }
    else if (tokenType == JavaTokenType.ASTERISK) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = Double.valueOf(((Number)lOperandValue).doubleValue() * ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = Float.valueOf(((Number)lOperandValue).floatValue() * ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          final long l = ((Number)lOperandValue).longValue();
          final long r = ((Number)rOperandValue).longValue();
          value = Long.valueOf(l * r);
          checkMultiplicationOverflow(((Long)value).longValue(), l, r, expression);
        }
        else {
          final int l = ((Number)lOperandValue).intValue();
          final int r = ((Number)rOperandValue).intValue();
          value = Integer.valueOf(l * r);
          checkMultiplicationOverflow(((Integer)value).intValue(), l, r, expression);
        }
      }
    }
    else if (tokenType == JavaTokenType.DIV) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = Double.valueOf(((Number)lOperandValue).doubleValue() / ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = Float.valueOf(((Number)lOperandValue).floatValue() / ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          final long r = ((Number)rOperandValue).longValue();
          final long l = ((Number)lOperandValue).longValue();
          checkDivisionOverflow(l, r, Long.MIN_VALUE, expression);
          value = r == 0 ? null : Long.valueOf(l / r);
        }
        else {
          final int r = ((Number)rOperandValue).intValue();
          final int l = ((Number)lOperandValue).intValue();
          checkDivisionOverflow(l, r, Integer.MIN_VALUE, expression);
          value = r == 0 ? null : Integer.valueOf(l / r);
        }
      }
    }
    else if (tokenType == JavaTokenType.PERC) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        double rVal = ((Number)rOperandValue).doubleValue();
        if (myThrowExceptionOnOverflow && rVal == 0) throw new ConstantEvaluationOverflowException(expression);
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = Double.valueOf(((Number)lOperandValue).doubleValue() % ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = Float.valueOf(((Number)lOperandValue).floatValue() % ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          final long l = ((Number)lOperandValue).longValue();
          final long r = ((Number)rOperandValue).longValue();
          checkDivisionOverflow(l, r, Long.MIN_VALUE, expression);
          value = r == 0 ? null : Long.valueOf(l % r);
        }
        else {
          final int l = ((Number)lOperandValue).intValue();
          final int r = ((Number)rOperandValue).intValue();
          checkDivisionOverflow(l, r, Integer.MIN_VALUE, expression);
          value = r == 0 ? null : Integer.valueOf(l % r);
        }
      }
    }
    else if (tokenType == JavaTokenType.LTLT) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long) {
          long l = ((Number)lOperandValue).longValue();
          long r = ((Number)rOperandValue).longValue();
          value = Long.valueOf(l << r);
          checkMultiplicationOverflow(((Long)value).longValue(), l, (long)Math.pow(2, r & 0x3F), expression);
        }
        else {
          int l = ((Number)lOperandValue).intValue();
          int r = ((Number)rOperandValue).intValue();
          value = Integer.valueOf(l << r);
          checkMultiplicationOverflow(((Integer)value).intValue(), l, (long)Math.pow(2, r & 0x1F), expression);
        }
      }
    }
    else if (tokenType == JavaTokenType.GTGT) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long) {
          value = Long.valueOf(((Number)lOperandValue).longValue() >> ((Number)rOperandValue).longValue());
        }
        else {
          value = Integer.valueOf(((Number)lOperandValue).intValue() >> ((Number)rOperandValue).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.GTGTGT) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long) {
          value = Long.valueOf(((Number)lOperandValue).longValue() >>> ((Number)rOperandValue).longValue());
        }
        else {
          value = Integer.valueOf(((Number)lOperandValue).intValue() >>> ((Number)rOperandValue).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.AND) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          value = Long.valueOf(((Number)lOperandValue).longValue() & ((Number)rOperandValue).longValue());
        }
        else {
          value = Integer.valueOf(((Number)lOperandValue).intValue() & ((Number)rOperandValue).intValue());
        }
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() && ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.OR) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          value = Long.valueOf(((Number)lOperandValue).longValue() | ((Number)rOperandValue).longValue());
        }
        else {
          value = Integer.valueOf(((Number)lOperandValue).intValue() | ((Number)rOperandValue).intValue());
        }
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() || ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.XOR) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          value = Long.valueOf(((Number)lOperandValue).longValue() ^ ((Number)rOperandValue).longValue());
        }
        else {
          value = Integer.valueOf(((Number)lOperandValue).intValue() ^ ((Number)rOperandValue).intValue());
        }
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() ^ ((Boolean)rOperandValue).booleanValue());
      }
    }
    return value;
  }

  private static String computeValueToString(Object value) {
    if (value instanceof PsiType) {
      if (value instanceof PsiArrayType) {
        return "class " + ClassUtil.getClassObjectPresentation((PsiType)value);
      }

      PsiClass psiClass = PsiUtil.resolveClassInType((PsiType)value);
      String prefix = psiClass == null ? "" : psiClass.isInterface() ? "interface " : "class ";
      return prefix + ((PsiType)value).getCanonicalText();
    }
    else {
      return value.toString();
    }
  }

  @Nullable
  private static Boolean handleEqualityComparison(Object lOperandValue, Object rOperandValue, IElementType tokenType) {
    if (lOperandValue instanceof String && rOperandValue instanceof String ||
        lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
      return lOperandValue.equals(rOperandValue) == (tokenType == JavaTokenType.EQEQ);
    }
    return compareNumbers(lOperandValue, rOperandValue, tokenType);
  }

  private static Boolean compareNumbers(Object o1, Object o2, IElementType op) {
    // JLS 15.20.1. Numerical Comparison Operators <, <=, >, and >=
    // JLS 15.21.1. Numerical Equality Operators == and !=
    // JLS 5.6.2 Binary Numeric Promotion
    if (o1 instanceof Character) o1 = (int)((Character)o1).charValue();
    if (o2 instanceof Character) o2 = (int)((Character)o2).charValue();
    if (!(o1 instanceof Number) || !(o2 instanceof Number)) return null;
    Number n1 = (Number)o1;
    Number n2 = (Number)o2;
    int result;
    if (n1 instanceof Double || n2 instanceof Double) {
      double v1 = n1.doubleValue();
      double v2 = n2.doubleValue();
      if (Double.isNaN(v1) || Double.isNaN(v2)) return op == JavaTokenType.NE;
      //Cannot use Double.compare as we don't need special treatment of 0.0 and -0.0 here
      //noinspection UseCompareMethod
      result = v1 < v2 ? -1 : v1 == v2 ? 0 : 1;
    } else if (n1 instanceof Float || n2 instanceof Float) {
      float v1 = n1.floatValue();
      float v2 = n2.floatValue();
      if (Float.isNaN(v1) || Float.isNaN(v2)) return op == JavaTokenType.NE;
      //Cannot use Float.compare as we don't need special treatment of 0.0 and -0.0 here
      //noinspection UseCompareMethod
      result = v1 < v2 ? -1 : v1 == v2 ? 0 : 1;
    } else if (n1 instanceof Long || n2 instanceof Long) {
      result = Long.compare(n1.longValue(), n2.longValue());
    } else {
      result = Integer.compare(n1.intValue(), n2.intValue());
    }
    if (op == JavaTokenType.EQEQ) return result == 0;
    if (op == JavaTokenType.LT) return result < 0;
    if (op == JavaTokenType.LE) return result <= 0;
    if (op == JavaTokenType.GT) return result > 0;
    if (op == JavaTokenType.GE) return result >= 0;
    if (op == JavaTokenType.NE) return result != 0;
    throw new IllegalArgumentException("Unexpected operator: " + op);
  }

  @Override public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
    PsiExpression operand = expression.getOperand();
    Object operandValue = getStoredValue(operand);
    if (operandValue == null) {
      myResult = null;
      return;
    }
    IElementType tokenType = expression.getOperationTokenType();
    Object value = null;
    if (tokenType == JavaTokenType.MINUS) {
      if (operandValue instanceof Character) operandValue = Integer.valueOf(((Character)operandValue).charValue());
      if (operandValue instanceof Number) {
        if (operandValue instanceof Double) {
          value = Double.valueOf(-((Number)operandValue).doubleValue());
        }
        else if (operandValue instanceof Float) {
          value = Float.valueOf(-((Number)operandValue).floatValue());
        }
        else if (operandValue instanceof Long) {
          value = Long.valueOf(-((Number)operandValue).longValue());
          if (myThrowExceptionOnOverflow
              && !(operand instanceof PsiLiteralExpression)
              && ((Number)operandValue).longValue() == Long.MIN_VALUE) {
            throw new ConstantEvaluationOverflowException(expression);
          }
        }
        else {
          value = Integer.valueOf(-((Number)operandValue).intValue());
          if (myThrowExceptionOnOverflow
              && !(operand instanceof PsiLiteralExpression)
              && ((Number)operandValue).intValue() == Integer.MIN_VALUE) {
            throw new ConstantEvaluationOverflowException(expression);
          }
        }
      }
    }
    else if (tokenType == JavaTokenType.PLUS) {
      if (operandValue instanceof Character) operandValue = Integer.valueOf(((Character)operandValue).charValue());
      if (operandValue instanceof Number) {
        value = operandValue;
      }
    }
    else if (tokenType == JavaTokenType.TILDE) {
      if (operandValue instanceof Character) operandValue = Integer.valueOf(((Character)operandValue).charValue());
      if (isIntegral(operandValue)) {
        if (operandValue instanceof Long) {
          value = Long.valueOf(~((Number)operandValue).longValue());
        }
        else {
          value = Integer.valueOf(~((Number)operandValue).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.EXCL) {
      if (operandValue instanceof Boolean) {
        value = Boolean.valueOf(!((Boolean)operandValue).booleanValue());
      }
    }

    myResult = value;
  }

  @Override public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
    myResult = getStoredValue(expression.getExpression());
  }

  @Override
  public void visitMethodCallExpression(final @NotNull PsiMethodCallExpression expression) {
    myResult = myAuxEvaluator != null? myAuxEvaluator.computeExpression(expression, this) : null;
  }

  @Override
  public void visitClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
    PsiType type = expression.getOperand().getType();
    if (type instanceof PsiClassReferenceType) {
      PsiClass aClass = ((PsiClassReferenceType)type).resolve();
      if (aClass != null) {
        type = JavaPsiFacade.getElementFactory(expression.getProject()).createType(aClass, ((PsiClassReferenceType)type).getParameters());
      }
    }
    myResult = type;
  }

  @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    while (qualifierExpression != null) {
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        myResult = null;
        return;
      }

      PsiReferenceExpression qualifier = (PsiReferenceExpression) qualifierExpression;
      final PsiElement resolved = qualifier.resolve();
      if (resolved instanceof PsiPackage) break;
      if (!(resolved instanceof PsiClass)) {
        myResult = null;
        return;
      }
      qualifierExpression = ((PsiReferenceExpression) qualifierExpression).getQualifierExpression();
    }

    PsiElement resolvedExpression = expression.resolve();
    if (resolvedExpression instanceof PsiEnumConstant) {
      String constant = ((PsiEnumConstant)resolvedExpression).getName();
      PsiReferenceExpression qualifier = (PsiReferenceExpression)expression.getQualifier();
      if (qualifier == null) return;
      PsiElement element = qualifier.resolve();
      if (!(element instanceof PsiClass)) return;
      String name = ClassUtil.getJVMClassName((PsiClass)element);
      try {
        Class aClass = Class.forName(name);
        //noinspection unchecked
        myResult = Enum.valueOf(aClass, constant);
      }
      catch (Throwable ignore) { }
      return;
    }
    if (resolvedExpression instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable) resolvedExpression;
      // avoid cycles
      if (myVisitedVars != null && myVisitedVars.contains(variable)) {
        myResult = null;
        return;
      }

      Set<PsiVariable> oldVisitedVars = myVisitedVars;
      if (myVisitedVars == null) {
        myVisitedVars = new HashSet<>();
      }

      myVisitedVars.add(variable);
      try {
        myResult = variable instanceof PsiVariableEx? ((PsiVariableEx) variable).computeConstantValue(myVisitedVars) : null;
        if (myResult == null && myAuxEvaluator != null) myResult = myAuxEvaluator.computeExpression(expression, this);
        return;
      }
      finally {
        myVisitedVars.remove(variable);
        myVisitedVars = oldVisitedVars;
      }
    }

    myResult = null;
  }

  private static boolean isIntegral(Object o) {
    return o instanceof Long || o instanceof Integer || o instanceof Short || o instanceof Byte || o instanceof Character;
  }

  private void checkDivisionOverflow(long l, final long r, long minValue, PsiElement expression) {
    if (!myThrowExceptionOnOverflow) return;
    if (r == 0) throw new ConstantEvaluationOverflowException(expression);
    if (r == -1 && l == minValue) throw new ConstantEvaluationOverflowException(expression);
  }

  private void checkMultiplicationOverflow(long result, long l, long r, PsiElement expression) {
    if (!myThrowExceptionOnOverflow) return;
    if (r == 0 || l == 0) return;
    if (result / r != l || ((l < 0) ^ (r < 0) != (result < 0))) throw new ConstantEvaluationOverflowException(expression);
  }

  private void checkAdditionOverflow(boolean resultPositive, boolean lPositive, boolean rPositive, PsiElement expression) {
    if (!myThrowExceptionOnOverflow) return;
    boolean overflow = lPositive == rPositive && lPositive != resultPositive;
    if (overflow) throw new ConstantEvaluationOverflowException(expression);
  }

  private void checkRealNumberOverflow(Object result, Object lOperandValue, Object rOperandValue, PsiElement expression) {
    if (!myThrowExceptionOnOverflow) return;
    if (lOperandValue instanceof Float && ((Float) lOperandValue).isInfinite()) return;
    if (lOperandValue instanceof Double && ((Double) lOperandValue).isInfinite()) return;
    if (rOperandValue instanceof Float && ((Float) rOperandValue).isInfinite()) return;
    if (rOperandValue instanceof Double && ((Double) rOperandValue).isInfinite()) return;

    if (result instanceof Float && ((Float)result).isInfinite()) throw new ConstantEvaluationOverflowException(expression);
    if (result instanceof Double && ((Double)result).isInfinite()) throw new ConstantEvaluationOverflowException(expression);
  }

  @Override
  public Object computeExpression(@NotNull final PsiExpression expression, @NotNull final PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expression, myVisitedVars, myThrowExceptionOnOverflow, auxEvaluator);
  }

  @NotNull
  @Override
  public ConcurrentMap<PsiElement, Object> getCacheMap(final boolean overflow) {
    throw new AssertionError("should not be called");
  }
}
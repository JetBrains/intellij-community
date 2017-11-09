/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("UnnecessaryBoxing")
class ConstantExpressionVisitor extends JavaElementVisitor implements PsiConstantEvaluationHelper.AuxEvaluator {
  
  private final StringInterner myInterner = new StringInterner();

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
  public void visitLiteralExpression(PsiLiteralExpression expression) {
    final Object value = expression.getValue();
    myResult = value instanceof String ? myInterner.intern((String)value) : value;
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    final PsiTypeElement castTypeElement = expression.getCastType();

    PsiExpression operand = expression.getOperand();
    Object opValue = getStoredValue(operand);
    if(castTypeElement == null || opValue == null) {
      myResult = null;
      return;
    }

    PsiType castType = castTypeElement.getType();
    myResult = ConstantExpressionUtil.computeCastTo(opValue, castType);
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
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
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
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
        String l = lOperandValue.toString();
        String r = rOperandValue.toString();
        value = l + r;
      }
      else {
        if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
        if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());

        if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
          if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
            value = new Double(((Number)lOperandValue).doubleValue() + ((Number)rOperandValue).doubleValue());
            checkRealNumberOverflow(value, lOperandValue, rOperandValue,expression);
          }
          else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
            value = new Float(((Number)lOperandValue).floatValue() + ((Number)rOperandValue).floatValue());
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
          value = new Double(((Number)lOperandValue).doubleValue() - ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() - ((Number)rOperandValue).floatValue());
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
      if (lOperandValue instanceof Boolean && !((Boolean)lOperandValue).booleanValue() ||
          rOperandValue instanceof Boolean && !((Boolean)rOperandValue).booleanValue()) {
        value = Boolean.FALSE;
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() && ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.OROR) {
      if (lOperandValue instanceof Boolean && ((Boolean)lOperandValue).booleanValue() ||
          rOperandValue instanceof Boolean && ((Boolean)rOperandValue).booleanValue()) {
        value = Boolean.TRUE;
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() || ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.LT) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() < ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.LE) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() <= ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.GT) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() > ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.GE) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() >= ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.EQEQ) {
      value = areValuesEqual(lOperandValue, rOperandValue, expression);
    }
    else if (tokenType == JavaTokenType.NE) {
      Boolean eq = areValuesEqual(lOperandValue, rOperandValue, expression);
      if (eq != null) value = !eq;
    }
    else if (tokenType == JavaTokenType.ASTERISK) {
      if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = new Double(((Number)lOperandValue).doubleValue() * ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() * ((Number)rOperandValue).floatValue());
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
          value = new Double(((Number)lOperandValue).doubleValue() / ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() / ((Number)rOperandValue).floatValue());
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
          value = new Double(((Number)lOperandValue).doubleValue() % ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() % ((Number)rOperandValue).floatValue());
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

  @Nullable
  private static Boolean areValuesEqual(Object lOperandValue, Object rOperandValue, PsiPolyadicExpression expression) {
    if (lOperandValue instanceof Character) lOperandValue = Integer.valueOf(((Character)lOperandValue).charValue());
    if (rOperandValue instanceof Character) rOperandValue = Integer.valueOf(((Character)rOperandValue).charValue());
    if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
      return isBoxedComparison(expression) ? lOperandValue == rOperandValue
                                           : ((Number)lOperandValue).doubleValue() == ((Number)rOperandValue).doubleValue();
    }
    if (lOperandValue instanceof String && rOperandValue instanceof String ||
        lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
      return lOperandValue.equals(rOperandValue);
    }
    return null;
  }

  private static boolean isBoxedComparison(PsiPolyadicExpression expression) {
    return Arrays.stream(expression.getOperands()).anyMatch(op -> op.getType() instanceof PsiClassType);
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
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
          value = new Double(-((Number)operandValue).doubleValue());
        }
        else if (operandValue instanceof Float) {
          value = new Float(-((Number)operandValue).floatValue());
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

  @Override public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    myResult = getStoredValue(expression.getExpression());
  }

  @Override
  public void visitMethodCallExpression(final PsiMethodCallExpression expression) {
    myResult = myAuxEvaluator != null? myAuxEvaluator.computeExpression(expression, this) : null;
  }

  @Override
  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    myResult = expression.getOperand().getType();
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
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
      if (constant == null) return;
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
      if (myVisitedVars == null) { myVisitedVars = new THashSet<>(); }

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
  public Object computeExpression(final PsiExpression expression, final PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expression, myVisitedVars, myThrowExceptionOnOverflow, auxEvaluator);
  }

  @Override
  public ConcurrentMap<PsiElement, Object> getCacheMap(final boolean overflow) {
    throw new AssertionError("should not be called");
  }
}
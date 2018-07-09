// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class BinaryExpressionEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

class BinaryExpressionEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.BinaryExpressionEvaluator");
  private final Evaluator myLeftOperand;
  private final Evaluator myRightOperand;
  private final IElementType myOpType;
  private final String myExpectedType; // a result of PsiType.getCanonicalText()

  public BinaryExpressionEvaluator(@NotNull Evaluator leftOperand,
                                   @NotNull Evaluator rightOperand,
                                   @NotNull IElementType opType,
                                   String expectedType) {
    myLeftOperand = DisableGC.create(leftOperand);
    myRightOperand = DisableGC.create(rightOperand);
    myOpType = opType;
    myExpectedType = expectedType;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value leftResult = (Value)myLeftOperand.evaluate(context);
    return evaluateOperation(leftResult, myOpType, myRightOperand, myExpectedType, context);

  }

  static Object evaluateOperation(final Value leftResult,
                                  final IElementType opType,
                                  final Evaluator rightOperand,
                                  final String expectedType,
                                  final EvaluationContextImpl context) throws EvaluateException {
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (leftResult instanceof BooleanValue) {
      boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
      if (opType == JavaTokenType.OROR && v1) {
        return DebuggerUtilsEx.createValue(vm, expectedType, true);
      }
      if (opType == JavaTokenType.ANDAND && !v1) {
        return DebuggerUtilsEx.createValue(vm, expectedType, false);
      }
    }
    Value rightResult = (Value)rightOperand.evaluate(context);
    if (opType == JavaTokenType.PLUS) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 + v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        final double v1 = ((PrimitiveValue)leftResult).doubleValue();
        final double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 + v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 + v2);
      }
      if (leftResult instanceof StringReference || rightResult instanceof StringReference) {
        String v1 = DebuggerUtils.getValueAsString(context, leftResult);
        String v2 = DebuggerUtils.getValueAsString(context, rightResult);
        return DebuggerUtilsEx.mirrorOfString(v1 + v2, vm, context);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "+"));
    }
    else if (opType == JavaTokenType.MINUS) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 - v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 - v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 - v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "-"));
    }
    else if (opType == JavaTokenType.ASTERISK) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 * v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 * v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 * v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "*"));
    }
    else if (opType == JavaTokenType.DIV) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 / v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 / v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 / v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "/"));
    }
    else if (opType == JavaTokenType.PERC) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 % v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 % v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 % v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "%"));
    }
    else if (opType == JavaTokenType.LTLT) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        if (leftResult instanceof ByteValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((ByteValue)leftResult).byteValue() << v2);
        }
        else if (leftResult instanceof ShortValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((ShortValue)leftResult).shortValue() << v2);
        }
        else if (leftResult instanceof IntegerValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((IntegerValue)leftResult).intValue() << v2);
        }
        return DebuggerUtilsEx.createValue(vm, expectedType, ((PrimitiveValue)leftResult).longValue() << v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        return DebuggerUtilsEx.createValue(vm, expectedType, ((CharValue)leftResult).charValue() << ((CharValue)rightResult).charValue());
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "<<"));
    }
    else if (opType == JavaTokenType.GTGT) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        if (leftResult instanceof ByteValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((ByteValue)leftResult).byteValue() >> v2);
        }
        else if (leftResult instanceof ShortValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((ShortValue)leftResult).shortValue() >> v2);
        }
        else if (leftResult instanceof IntegerValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((IntegerValue)leftResult).intValue() >> v2);
        }
        return DebuggerUtilsEx.createValue(vm, expectedType, ((PrimitiveValue)leftResult).longValue() >> v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        return DebuggerUtilsEx.createValue(vm, expectedType, ((CharValue)leftResult).charValue() >> ((CharValue)rightResult).charValue());
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">>"));
    }
    else if (opType == JavaTokenType.GTGTGT) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        if (leftResult instanceof ByteValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((ByteValue)leftResult).byteValue() >>> v2);
        }
        else if (leftResult instanceof ShortValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((ShortValue)leftResult).shortValue() >>> v2);
        }
        else if (leftResult instanceof IntegerValue) {
          return DebuggerUtilsEx.createValue(vm, expectedType, ((IntegerValue)leftResult).intValue() >>> v2);
        }
        return DebuggerUtilsEx.createValue(vm, expectedType, ((PrimitiveValue)leftResult).longValue() >>> v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        return DebuggerUtilsEx.createValue(vm, expectedType, ((CharValue)leftResult).charValue() >>> ((CharValue)rightResult).charValue());
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">>>"));
    }
    else if (opType == JavaTokenType.AND) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 & v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 & v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 & v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "&"));
    }
    else if (opType == JavaTokenType.OR) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 | v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 | v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 | v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "|"));
    }
    else if (opType == JavaTokenType.XOR) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 ^ v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 ^ v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 ^ v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "^"));
    }
    else if (opType == JavaTokenType.EQEQ) {
      if (leftResult == null && rightResult == null) {
        return DebuggerUtilsEx.createValue(vm, expectedType, true);
      }
      if (leftResult == null) {
        return DebuggerUtilsEx.createValue(vm, expectedType, rightResult.equals(null));
      }
      if (rightResult == null) {
        return DebuggerUtilsEx.createValue(vm, expectedType, leftResult.equals(null));
      }
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 == v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 == v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 == v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 == v2);
      }
      if (leftResult instanceof ObjectReference && rightResult instanceof ObjectReference) {
        ObjectReference v1 = (ObjectReference)leftResult;
        ObjectReference v2 = (ObjectReference)rightResult;
        return DebuggerUtilsEx.createValue(vm, expectedType, v1.uniqueID() == v2.uniqueID());
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "=="));
    }
    else if (opType == JavaTokenType.OROR) {
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 || v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "||"));
    }
    else if (opType == JavaTokenType.ANDAND) {
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 && v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "&&"));
    }
    else if (opType == JavaTokenType.NE) {
      if (leftResult == null && rightResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, false);
      if (leftResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, !rightResult.equals(null));
      if (rightResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, !leftResult.equals(null));
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 != v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 != v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 != v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 != v2);
      }
      if (leftResult instanceof ObjectReference && rightResult instanceof ObjectReference) {
        ObjectReference v1 = (ObjectReference)leftResult;
        ObjectReference v2 = (ObjectReference)rightResult;
        return DebuggerUtilsEx.createValue(vm, expectedType, v1.uniqueID() != v2.uniqueID());
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "!="));
    }
    else if (opType == JavaTokenType.LT) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 < v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 < v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 < v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "<"));
    }
    else if (opType == JavaTokenType.GT) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 > v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 > v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 > v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">"));
    }
    else if (opType == JavaTokenType.LE) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 <= v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 <= v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 <= v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "<="));
    }
    else if (opType == JavaTokenType.GE) {
      if (DebuggerUtils.isInteger(leftResult) && DebuggerUtils.isInteger(rightResult)) {
        final long v1 = ((PrimitiveValue)leftResult).longValue();
        final long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >= v2);
      }
      if (DebuggerUtils.isNumeric(leftResult) && DebuggerUtils.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >= v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >= v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">="));
    }

    LOG.assertTrue(false);

    return null;
  }

  
}

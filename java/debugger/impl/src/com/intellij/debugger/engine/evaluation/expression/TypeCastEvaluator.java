// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class TypeCastEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

public class TypeCastEvaluator implements Evaluator {
  private final Evaluator myOperandEvaluator;
  private final String myPrimitiveCastType;
  private final TypeEvaluator myTypeCastEvaluator;

  public TypeCastEvaluator(Evaluator operandEvaluator, @NotNull TypeEvaluator typeCastEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myPrimitiveCastType = null;
    myTypeCastEvaluator = typeCastEvaluator;
  }

  public TypeCastEvaluator(Evaluator operandEvaluator, @NotNull String primitiveType) {
    myOperandEvaluator = operandEvaluator;
    myPrimitiveCastType = primitiveType;
    myTypeCastEvaluator = null;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      if (myPrimitiveCastType != null) {
        throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.cannot.cast.null", myPrimitiveCastType));
      }
      return null;
    }
    VirtualMachineProxyImpl vm = context.getVirtualMachineProxy();
    if (DebuggerUtils.isInteger(value)) {
      value = DebuggerUtilsEx.createValue(vm, myPrimitiveCastType, ((PrimitiveValue)value).longValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.cast.numeric",
                                                                                       myPrimitiveCastType));
      }
    }
    else if (DebuggerUtils.isNumeric(value)) {
      value = DebuggerUtilsEx.createValue(vm, myPrimitiveCastType, ((PrimitiveValue)value).doubleValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.cast.numeric",
                                                                                       myPrimitiveCastType));
      }
    }
    else if (value instanceof BooleanValue) {
      value = DebuggerUtilsEx.createValue(vm, myPrimitiveCastType, ((BooleanValue)value).booleanValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.cast.boolean",
                                                                                       myPrimitiveCastType));
      }
    }
    else if (value instanceof CharValue) {
      value = DebuggerUtilsEx.createValue(vm, myPrimitiveCastType, ((CharValue)value).charValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.cannot.cast.char", myPrimitiveCastType));
      }
    }
    else if (value instanceof ObjectReference) {
      ReferenceType type = ((ObjectReference)value).referenceType();
      if (myTypeCastEvaluator == null) {
        throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.cannot.cast.object", type.name(), myPrimitiveCastType));
      }
      ReferenceType castType = myTypeCastEvaluator.evaluate(context);
      if (!DebuggerUtilsImpl.instanceOf(type, castType)) {
        throw EvaluateExceptionUtil.createEvaluateException(
          JavaDebuggerBundle.message("evaluation.error.cannot.cast.object", type.name(), castType.name()));
      }
    }

    return value;
  }
}

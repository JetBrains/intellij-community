// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class TypeCastEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
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
  private final String myCastType;
  private final TypeEvaluator myTypeCastEvaluator;

  public TypeCastEvaluator(Evaluator operandEvaluator, @NotNull TypeEvaluator typeCastEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myCastType = null;
    myTypeCastEvaluator = typeCastEvaluator;
  }

  public TypeCastEvaluator(Evaluator operandEvaluator, @NotNull String primitiveType) {
    myOperandEvaluator = operandEvaluator;
    myCastType = primitiveType;
    myTypeCastEvaluator = null;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      if (myCastType != null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.null", myCastType));
      }
      return null;
    }
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (DebuggerUtils.isInteger(value)) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((PrimitiveValue)value).longValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.numeric", myCastType));
      }
    }
    else if (DebuggerUtils.isNumeric(value)) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((PrimitiveValue)value).doubleValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.numeric", myCastType));
      }
    }
    else if (value instanceof BooleanValue) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((BooleanValue)value).booleanValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.boolean", myCastType));
      }
    }
    else if (value instanceof CharValue) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((CharValue)value).charValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.char", myCastType));
      }
    }
    else if (value instanceof ObjectReference) {
      ReferenceType type = ((ObjectReference)value).referenceType();
      if (myTypeCastEvaluator == null) {
        throw EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("evaluation.error.cannot.cast.object", type.name(), myCastType));
      }
      ReferenceType castType = (ReferenceType)myTypeCastEvaluator.evaluate(context);
      if (!DebuggerUtilsImpl.instanceOf(type, castType)) {
        throw EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("evaluation.error.cannot.cast.object", type.name(), castType.name()));
      }
    }

    return value;
  }
}

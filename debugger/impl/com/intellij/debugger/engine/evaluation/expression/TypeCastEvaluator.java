/*
 * Class TypeCastEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;

public class TypeCastEvaluator implements Evaluator {
  private final Evaluator myOperandEvaluator;
  private final String myCastType;
  private final boolean myIsPrimitive;

  public TypeCastEvaluator(Evaluator operandEvaluator, String castType, boolean isPrimitive) {
    myOperandEvaluator = operandEvaluator;
    myCastType = castType;
    myIsPrimitive = isPrimitive;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      if (myIsPrimitive) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.null", myCastType));
      }
      return null;
    }
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (DebuggerUtilsEx.isInteger(value)) {
      value = DebuggerUtilsEx.createValue(vm, myCastType, ((PrimitiveValue)value).longValue());
      if (value == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.numeric", myCastType));
      }
    }
    else if (DebuggerUtilsEx.isNumeric(value)) {
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
    return value;
  }
}

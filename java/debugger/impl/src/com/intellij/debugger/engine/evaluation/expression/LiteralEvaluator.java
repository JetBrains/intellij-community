/*
 * Class LiteralEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;

class LiteralEvaluator implements Evaluator {
  private final Object myValue;
  private final String myExpectedType;

  public LiteralEvaluator(Object value, String expectedType) {
    myValue = value;
    myExpectedType = expectedType;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    if (myValue == null) {
      return null;
    }
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (myValue instanceof Boolean) {
      return DebuggerUtilsEx.createValue(vm, myExpectedType, ((Boolean)myValue).booleanValue());
    }
    if (myValue instanceof Character) {
      return DebuggerUtilsEx.createValue(vm, myExpectedType, ((Character)myValue).charValue());
    }
    if (myValue instanceof Double) {
      return DebuggerUtilsEx.createValue(vm, myExpectedType, ((Number)myValue).doubleValue());
    }
    if (myValue instanceof Float) {
      return DebuggerUtilsEx.createValue(vm, myExpectedType, ((Number)myValue).floatValue());
    }
    if (myValue instanceof Number) {
      return DebuggerUtilsEx.createValue(vm, myExpectedType, ((Number)myValue).longValue());
    }
    if (myValue instanceof String) {
      return vm.mirrorOf((String)myValue);
    }
    throw EvaluateExceptionUtil
      .createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", myExpectedType));
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class LiteralEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jdi.Method;
import com.sun.jdi.StringReference;

import java.util.Collections;

class LiteralEvaluator implements Evaluator {
  private final Object myValue;
  private final String myExpectedType;

  LiteralEvaluator(Object value, String expectedType) {
    myValue = value;
    myExpectedType = expectedType;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    if (myValue == null) {
      return null;
    }
    VirtualMachineProxyImpl vm = context.getVirtualMachineProxy();
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
      return vm.mirrorOfStringLiteral(((String)myValue), () -> {
        StringReference str = DebuggerUtilsEx.mirrorOfString((String)myValue, context);
        // intern starting from jdk 7
        if (Registry.is("debugger.intern.string.literals") && vm.versionHigher("1.7")) {
          Method internMethod = DebuggerUtils.findMethod(str.referenceType(), "intern", "()Ljava/lang/String;");
          if (internMethod != null) {
            return (StringReference)context.getDebugProcess().invokeMethod(context, str, internMethod, Collections.emptyList());
          }
        }
        return str;
      });
    }
    throw EvaluateExceptionUtil
      .createEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", myExpectedType));
  }

  @Override
  public String toString() {
    return myValue != null ? myValue.toString() : "null";
  }
}

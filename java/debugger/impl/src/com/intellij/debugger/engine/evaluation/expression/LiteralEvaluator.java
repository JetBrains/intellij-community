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
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.StringReference;

import java.util.Collections;

class LiteralEvaluator implements Evaluator {
  private final Object myValue;
  private final String myExpectedType;

  public LiteralEvaluator(Object value, String expectedType) {
    myValue = value;
    myExpectedType = expectedType;
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
      StringReference str = vm.mirrorOf((String)myValue);
      // intern starting from jdk 7
      if (vm.versionHigher("1.7")) {
        Method internMethod = ((ClassType)str.referenceType()).concreteMethodByName("intern", "()Ljava/lang/String;");
        if (internMethod != null) {
          return context.getDebugProcess().invokeMethod(context, str, internMethod, Collections.emptyList());
        }
      }
      return str;
    }
    throw EvaluateExceptionUtil
      .createEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", myExpectedType));
  }

  @Override
  public String toString() {
    return myValue != null ? myValue.toString() : "null";
  }
}

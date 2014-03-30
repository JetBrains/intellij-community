/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.sun.jdi.*;

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
      Type type = value.type();
      if (!DebuggerUtils.instanceOf(type, myCastType)) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.cannot.cast.object", type.name(), myCastType));
      }
    }

    return value;
  }
}

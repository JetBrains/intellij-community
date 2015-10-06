/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Class UnaryExpressionEvaluator
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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;

class UnaryExpressionEvaluator implements Evaluator {
  private final IElementType myOperationType;
  private final String myExpectedType;
  private final Evaluator myOperandEvaluator;
  private final String myOperationText;

  public UnaryExpressionEvaluator(IElementType operationType, String expectedType, Evaluator operandEvaluator, final String operationText) {
    myOperationType = operationType;
    myExpectedType = expectedType;
    myOperandEvaluator = operandEvaluator;
    myOperationText = operationText;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value operand = (Value)myOperandEvaluator.evaluate(context);
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (myOperationType == JavaTokenType.PLUS) {
      if (DebuggerUtils.isNumeric(operand)) {
        return operand;
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.numeric.expected"));
    }
    else if (myOperationType == JavaTokenType.MINUS) {
      if (DebuggerUtils.isInteger(operand)) {
        long v = ((PrimitiveValue)operand).longValue();
        return DebuggerUtilsEx.createValue(vm, myExpectedType, -v);
      }
      if (DebuggerUtils.isNumeric(operand)) {
        double v = ((PrimitiveValue)operand).doubleValue();
        return DebuggerUtilsEx.createValue(vm, myExpectedType, -v);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.numeric.expected"));
    }
    else if (myOperationType == JavaTokenType.TILDE) {
      if (DebuggerUtils.isInteger(operand)) {
        long v = ((PrimitiveValue)operand).longValue();
        return DebuggerUtilsEx.createValue(vm, myExpectedType, ~v);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.integer.expected"));
    }
    else if (myOperationType == JavaTokenType.EXCL) {
      if (operand instanceof BooleanValue) {
        boolean v = ((BooleanValue)operand).booleanValue();
        return DebuggerUtilsEx.createValue(vm, myExpectedType, !v);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.boolean.expected"));
    }
    
    throw EvaluateExceptionUtil.createEvaluateException(
      DebuggerBundle.message("evaluation.error.operation.not.supported", myOperationText)
    );
  }
}

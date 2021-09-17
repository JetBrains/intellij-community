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
 * Class ConditionalExpressionEvaluator
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
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ConditionalExpressionEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myThenEvaluator;
  private final Evaluator myElseEvaluator;
  private final String myExpectedType;

  ConditionalExpressionEvaluator(Evaluator conditionEvaluator,
                                 Evaluator thenEvaluator,
                                 Evaluator elseEvaluator,
                                 @Nullable String expectedType) {
    myConditionEvaluator = conditionEvaluator;
    myThenEvaluator = thenEvaluator;
    myElseEvaluator = elseEvaluator;
    myExpectedType = expectedType;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value condition = (Value)myConditionEvaluator.evaluate(context);
    if (!(condition instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.boolean.condition.expected"));
    }
    return doConversion(
      ((BooleanValue)condition).booleanValue() ? myThenEvaluator.evaluate(context) : myElseEvaluator.evaluate(context),
      myExpectedType,
      context
    );
  }

  static Object doConversion(Object conditionalResult,
                             @Nullable String expectedType,
                             EvaluationContextImpl context) throws EvaluateException {
    if (expectedType == null) {
      return conditionalResult;
    }

    Object castValue = null;
    if (conditionalResult instanceof ObjectReference &&
        UnBoxingEvaluator.isTypeUnboxable(((ObjectReference)conditionalResult).type().name()) &&
        DebuggerUtils.isPrimitiveType(expectedType)) {

      final Object unboxed = UnBoxingEvaluator.unbox(conditionalResult, context);
      castValue = tryCastValue(unboxed, expectedType, context);
    }
    else if (conditionalResult instanceof PrimitiveValue && DebuggerUtils.isPrimitiveType(expectedType)) {

      castValue = tryCastValue(conditionalResult, expectedType, context);
    }

    if (castValue != null) {
      return castValue;
    }

    return conditionalResult;
  }

  static @Nullable Object tryCastValue(@NotNull Object value,
                                       @NotNull String expectedType,
                                       @NotNull EvaluationContextImpl context) {
    final VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (value instanceof IntegerValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((IntegerValue)value).intValue());
    }
    else if (value instanceof ShortValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((ShortValue)value).shortValue());
    }
    else if (value instanceof ByteValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((ByteValue)value).byteValue());
    }
    else if (value instanceof LongValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((LongValue)value).longValue());
    }
    else if (value instanceof FloatValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((FloatValue)value).floatValue());
    }
    else if (value instanceof DoubleValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((DoubleValue)value).doubleValue());
    }
    else if (value instanceof CharValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((CharValue)value).charValue());
    }
    else if (value instanceof BooleanValue) {
      return DebuggerUtilsEx.createValue(vm, expectedType, ((BooleanValue)value).booleanValue());
    }

    return null;
  }
}

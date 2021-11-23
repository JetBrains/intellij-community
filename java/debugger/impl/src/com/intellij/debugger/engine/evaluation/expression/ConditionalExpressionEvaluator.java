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
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

class ConditionalExpressionEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myThenEvaluator;
  private final Evaluator myElseEvaluator;
  private final PsiType myExpectedType;

  ConditionalExpressionEvaluator(Evaluator conditionEvaluator,
                                 Evaluator thenEvaluator,
                                 Evaluator elseEvaluator,
                                 @Nullable PsiType expectedType) {
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
                             @Nullable PsiType expectedType,
                             EvaluationContextImpl context) throws EvaluateException {
    if (expectedType == null) {
      return conditionalResult;
    }

    Object castValue;
    if (conditionalResult instanceof ObjectReference &&
        UnBoxingEvaluator.isTypeUnboxable(((ObjectReference)conditionalResult).type().name()) &&
        DebuggerUtils.isPrimitiveType(expectedType.getCanonicalText())) {

      castValue = UnBoxingEvaluator.unbox(conditionalResult, context);
    } else {
      castValue = conditionalResult;
    }

    if (!(castValue instanceof Value)) {
      return conditionalResult;
    }

    if (expectedType instanceof PsiPrimitiveType) {
      return new TypeCastEvaluator(new IdentityEvaluator((Value)castValue), expectedType.getCanonicalText()).evaluate(context);
    }
    else {
      return new TypeCastEvaluator(
        new IdentityEvaluator((Value)castValue),
        new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expectedType))
      ).evaluate(context);
    }
  }
}

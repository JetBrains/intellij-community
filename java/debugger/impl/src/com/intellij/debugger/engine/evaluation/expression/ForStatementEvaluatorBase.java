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
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.BooleanValue;

/**
 * @author egor
 */
public abstract class ForStatementEvaluatorBase extends LoopEvaluator {
  public ForStatementEvaluatorBase(String labelName, Evaluator bodyEvaluator) {
    super(labelName, bodyEvaluator);
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid();
    value = evaluateInitialization(context, value);

    while (true) {
      // condition
      Object codition = evaluateCondition(context);
      if (codition instanceof Boolean) {
        if (!(Boolean)codition) break;
      }
      else if (codition instanceof BooleanValue) {
        if (!((BooleanValue)codition).booleanValue()) break;
      }
      else {
        throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
      }

      // body
      if (body(context)) break;

      // update
      value = evaluateUpdate(context, value);
    }

    return value;
  }

  protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
    return value;
  }

  protected Object evaluateCondition(EvaluationContextImpl context) throws EvaluateException {
    return true;
  }

  protected Object evaluateUpdate(EvaluationContextImpl context, Object value) throws EvaluateException {
    return value;
  }
}

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
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.Comparing;
import com.sun.jdi.BooleanValue;

/**
 * @author egor
 */
public abstract class ForStatementEvaluatorBase implements Evaluator {
  private final String myLabelName;

  public ForStatementEvaluatorBase(String labelName) {
    myLabelName = labelName;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = context.getDebugProcess().getVirtualMachineProxy().mirrorOf();
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

      try {
        evaluateBody(context);
      }
      catch (BreakException e) {
        if (Comparing.equal(e.getLabelName(), myLabelName)) {
          break;
        }
        else {
          throw e;
        }
      }
      catch (ContinueException e) {
        if (Comparing.equal(e.getLabelName(), myLabelName)) {
          //continue;
        }
        else {
          throw e;
        }
      }

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

  protected void evaluateBody(EvaluationContextImpl context) throws EvaluateException {
  }

  protected Object evaluateUpdate(EvaluationContextImpl context, Object value) throws EvaluateException {
    return value;
  }
}

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
 * @author lex
 */
public class IfStatementEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myThenEvaluator;
  private final Evaluator myElseEvaluator;

  private Modifier myModifier;

  public IfStatementEvaluator(Evaluator conditionEvaluator, Evaluator thenEvaluator, Evaluator elseEvaluator) {
    myConditionEvaluator = new DisableGC(conditionEvaluator);
    myThenEvaluator = new DisableGC(thenEvaluator);
    myElseEvaluator = elseEvaluator == null ? null : new DisableGC(elseEvaluator);
  }

  public Modifier getModifier() {
    return myModifier;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = myConditionEvaluator.evaluate(context);
    if(!(value instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
    } else {
      if(((BooleanValue)value).booleanValue()) {
        value = myThenEvaluator.evaluate(context);
        myModifier = myThenEvaluator.getModifier();
      }
      else {
        if(myElseEvaluator != null) {
          value = myElseEvaluator.evaluate(context);
          myModifier = myElseEvaluator.getModifier();
        } else {
          value = context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid();
          myModifier = null;
        }
      }
    }
    return value;
  }

}

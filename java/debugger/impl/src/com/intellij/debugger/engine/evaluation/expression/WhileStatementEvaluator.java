/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * @author lex
 */
public class WhileStatementEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myBodyEvaluator;
  private final String myLabelName;

  public WhileStatementEvaluator(Evaluator conditionEvaluator, Evaluator bodyEvaluator, String labelName) {
    myConditionEvaluator = conditionEvaluator;
    myBodyEvaluator = bodyEvaluator;
    myLabelName = labelName;
  }

  public Modifier getModifier() {
    return myConditionEvaluator.getModifier();
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value;
    while (true) {
      value = myConditionEvaluator.evaluate(context);
      if (!(value instanceof BooleanValue)) {
        throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
      }
      else {
        if (!((BooleanValue)value).booleanValue()) {
          break;
        }
      }
      try {
        myBodyEvaluator.evaluate(context);
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
        if (!Comparing.equal(e.getLabelName(), myLabelName)) {
          throw e;
        }
      }
    }

    return value;
  }

}

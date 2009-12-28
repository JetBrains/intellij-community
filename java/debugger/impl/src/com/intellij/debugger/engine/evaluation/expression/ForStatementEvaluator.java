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
public class ForStatementEvaluator implements Evaluator {
  private final Evaluator myInitializationEvaluator;
  private final Evaluator myConditionEvaluator;
  private final Evaluator myUpdateEvaluator;
  private final Evaluator myBodyEvaluator;

  private Modifier myModifier;
  private final String myLabelName;

  public ForStatementEvaluator(Evaluator initializationEvaluator,
                               Evaluator conditionEvaluator,
                               Evaluator updateEvaluator,
                               Evaluator bodyEvaluator,
                               String labelName) {
    myInitializationEvaluator = initializationEvaluator;
    myConditionEvaluator = conditionEvaluator;
    myUpdateEvaluator = updateEvaluator;
    myBodyEvaluator = bodyEvaluator;
    myLabelName = labelName;
  }

  public Modifier getModifier() {
    return myModifier;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = context.getDebugProcess().getVirtualMachineProxy().mirrorOf();
    if (myInitializationEvaluator != null) {
      value = myInitializationEvaluator.evaluate(context);
      myModifier = myInitializationEvaluator.getModifier();
    }

    while (true) {
      if (myConditionEvaluator != null) {
        value = myConditionEvaluator.evaluate(context);
        myModifier = myConditionEvaluator.getModifier();
        if (!(value instanceof BooleanValue)) {
          throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
        }
        else {
          if (!((BooleanValue)value).booleanValue()) {
            break;
          }
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
        if (Comparing.equal(e.getLabelName(), myLabelName)) {
          //continue;
        }
        else {
          throw e;
        }
      }

      if (myUpdateEvaluator != null) {
        value = myUpdateEvaluator.evaluate(context);
        myModifier = myUpdateEvaluator.getModifier();
      }
    }

    return value;
  }

}

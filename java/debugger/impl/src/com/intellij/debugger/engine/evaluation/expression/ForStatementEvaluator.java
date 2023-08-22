/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

public class ForStatementEvaluator extends ForStatementEvaluatorBase {
  private final Evaluator myInitializationEvaluator;
  private final Evaluator myConditionEvaluator;
  private final Evaluator myUpdateEvaluator;

  private Modifier myModifier;

  public ForStatementEvaluator(Evaluator initializationEvaluator,
                               Evaluator conditionEvaluator,
                               Evaluator updateEvaluator,
                               Evaluator bodyEvaluator,
                               String labelName) {
    super(labelName, bodyEvaluator);
    myInitializationEvaluator = initializationEvaluator != null ? DisableGC.create(initializationEvaluator) : null;
    myConditionEvaluator = conditionEvaluator != null ? DisableGC.create(conditionEvaluator) : null;
    myUpdateEvaluator = updateEvaluator != null ? DisableGC.create(updateEvaluator) : null;
  }

  @Override
  public Modifier getModifier() {
    return myModifier;
  }

  @Override
  protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
    if (myInitializationEvaluator != null) {
      value = myInitializationEvaluator.evaluate(context);
      myModifier = myInitializationEvaluator.getModifier();
    }
    return value;
  }

  @Override
  protected Object evaluateCondition(EvaluationContextImpl context) throws EvaluateException {
    if (myConditionEvaluator != null) {
      Object value = myConditionEvaluator.evaluate(context);
      myModifier = myConditionEvaluator.getModifier();
      return value;
    }
    return true;
  }

  @Override
  protected Object evaluateUpdate(EvaluationContextImpl context, Object value) throws EvaluateException {
    if (myUpdateEvaluator != null) {
      value = myUpdateEvaluator.evaluate(context);
      myModifier = myUpdateEvaluator.getModifier();
    }
    return value;
  }
}

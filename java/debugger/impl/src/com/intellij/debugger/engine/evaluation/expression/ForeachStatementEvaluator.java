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
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.Value;

/**
 * @author egor
 */
public class ForeachStatementEvaluator extends ForStatementEvaluatorBase {
  private final Evaluator myIterationParameterEvaluator;
  private final Evaluator myIterableEvaluator;
  private final Evaluator myBodyEvaluator;

  private Evaluator myConditionEvaluator;
  private Evaluator myNextEvaluator;

  private Modifier myModifier;

  public ForeachStatementEvaluator(Evaluator iterationParameterEvaluator,
                               Evaluator iterableEvaluator,
                               Evaluator bodyEvaluator,
                               String labelName) {
    super(labelName);
    myIterationParameterEvaluator = iterationParameterEvaluator;
    myIterableEvaluator = new DisableGC(iterableEvaluator);
    myBodyEvaluator = bodyEvaluator != null ? new DisableGC(bodyEvaluator) : null;
  }

  public Modifier getModifier() {
    return myModifier;
  }

  @Override
  protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
    Object iterator = new MethodEvaluator(myIterableEvaluator, null, "iterator", null, new Evaluator[0]).evaluate(context);
    IdentityEvaluator iteratorEvaluator = new IdentityEvaluator(((Value)iterator));
    myConditionEvaluator = new MethodEvaluator(iteratorEvaluator, null, "hasNext", null, new Evaluator[0]);
    myNextEvaluator = new AssignmentEvaluator(myIterationParameterEvaluator, new MethodEvaluator(iteratorEvaluator, null, "next", null, new Evaluator[0]));
    return value;
  }

  @Override
  protected Object evaluateCondition(EvaluationContextImpl context) throws EvaluateException {
    Object res = myConditionEvaluator.evaluate(context);
    myModifier = myConditionEvaluator.getModifier();
    return res;
  }

  @Override
  protected void evaluateBody(EvaluationContextImpl context) throws EvaluateException {
    myNextEvaluator.evaluate(context);
    if (myBodyEvaluator != null) {
      myBodyEvaluator.evaluate(context);
    }
  }
}

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

package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

class ArrayInitializerEvaluator implements Evaluator{
  private final Evaluator[] myValueEvaluators;

  public ArrayInitializerEvaluator(Evaluator[] valueEvaluators) {
    myValueEvaluators = valueEvaluators;
  }

  /**
   * @return an array of Values
   */
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object[] values = new Object[myValueEvaluators.length];
    for (int idx = 0; idx < myValueEvaluators.length; idx++) {
      Evaluator evaluator = myValueEvaluators[idx];
      values[idx] = evaluator.evaluate(context);
    }
    return values;
  }

  public Modifier getModifier() {
    return null;
  }
}

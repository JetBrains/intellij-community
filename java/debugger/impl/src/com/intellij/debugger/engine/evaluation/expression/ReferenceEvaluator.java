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

/**
 * @author egor
 */
public class ReferenceEvaluator extends LocalVariableEvaluator {
  private final FieldEvaluator myFieldEvaluator;
  private boolean myIsField;

  public ReferenceEvaluator(String localVariableName) {
    super(localVariableName, false);
    myFieldEvaluator = new FieldEvaluator(new ThisEvaluator(), FieldEvaluator.TargetClassFilter.ALL, localVariableName);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    try {
      return super.evaluate(context);
    }
    catch (EvaluateException e) {
      try {
        Object fieldValue = myFieldEvaluator.evaluate(context);
        myIsField = true;
        return fieldValue;
      }
      catch (EvaluateException e1) {
        throw e;
      }
    }
  }

  @Override
  public Modifier getModifier() {
    return myIsField ? myFieldEvaluator.getModifier() : super.getModifier();
  }
}

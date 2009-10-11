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

/*
 * Class ClassObjectEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.DebuggerBundle;
import com.sun.jdi.ReferenceType;

public class ClassObjectEvaluator implements Evaluator {
  private final TypeEvaluator myTypeEvaluator;

  public ClassObjectEvaluator(TypeEvaluator typeEvaluator) {
    myTypeEvaluator = typeEvaluator;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object object = myTypeEvaluator.evaluate(context);
    if (!(object instanceof ReferenceType)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.reference.type.expected"));
    }
    return ((ReferenceType)object).classObject();
  }
}

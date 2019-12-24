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

/*
 * Class ThisEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class ThisEvaluator implements Evaluator {
  private final CaptureTraverser myTraverser;

  public ThisEvaluator() {
    this(CaptureTraverser.direct());
  }

  public ThisEvaluator(CaptureTraverser traverser) {
    myTraverser = traverser;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value objRef = myTraverser.traverse((ObjectReference)context.computeThisObject());
    if(objRef == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.this.not.avalilable"));
    }
    return objRef;
  }

  @Override
  public String toString() {
    return "this";
  }
}

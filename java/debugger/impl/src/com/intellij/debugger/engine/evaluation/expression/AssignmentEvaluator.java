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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.*;

/**
 * @author lex
 */
public class AssignmentEvaluator implements Evaluator{
  private final Evaluator myLeftEvaluator;
  private final Evaluator myRightEvaluator;

  public AssignmentEvaluator(Evaluator leftEvaluator, Evaluator rightEvaluator) {
    myLeftEvaluator = leftEvaluator;
    myRightEvaluator = rightEvaluator;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object right = myRightEvaluator.evaluate(context);
    if(right != null && !(right instanceof Value)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.not.rvalue"));
    }

    myLeftEvaluator.evaluate(context);
    Modifier modifier = myLeftEvaluator.getModifier();
    assign(modifier, right, context);
    return right;
  }

  static void assign(Modifier modifier, Object right, EvaluationContextImpl context) throws EvaluateException {
    if(modifier == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.not.lvalue"));
    }
    try {
      modifier.setValue(((Value)right));
    }
    catch (ClassNotLoadedException e) {
      if (!context.isAutoLoadClasses()) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      try {
        context.getDebugProcess().loadClass(context, e.className(), context.getClassLoader());
      }
      catch (InvocationException e1) {
        throw EvaluateExceptionUtil.createEvaluateException(e1);
      }
      catch (ClassNotLoadedException e1) {
        throw EvaluateExceptionUtil.createEvaluateException(e1);
      }
      catch (IncompatibleThreadStateException e1) {
        throw EvaluateExceptionUtil.createEvaluateException(e1);
      }
      catch (InvalidTypeException e1) {
        throw EvaluateExceptionUtil.createEvaluateException(e1);
      }
    }
    catch (InvalidTypeException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  public Modifier getModifier() {
    return myLeftEvaluator.getModifier();
  }
}

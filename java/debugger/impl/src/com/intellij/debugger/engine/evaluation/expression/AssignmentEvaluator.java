// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author lex
 */
public class AssignmentEvaluator implements Evaluator{
  private final Evaluator myLeftEvaluator;
  private final Evaluator myRightEvaluator;

  public AssignmentEvaluator(@NotNull Evaluator leftEvaluator, @NotNull Evaluator rightEvaluator) {
    myLeftEvaluator = leftEvaluator;
    myRightEvaluator = DisableGC.create(rightEvaluator);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    myLeftEvaluator.evaluate(context);
    final Modifier modifier = myLeftEvaluator.getModifier();

    final Object right = myRightEvaluator.evaluate(context);
    if(right != null && !(right instanceof Value)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.not.rvalue"));
    }

    assign(modifier, right, context);

    return right;
  }

  static void assign(Modifier modifier, Object right, EvaluationContextImpl context) throws EvaluateException {
    if(modifier == null) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.not.lvalue"));
    }
    try {
      modifier.setValue(((Value)right));
    }
    catch (ClassNotLoadedException e) {
      if (!context.isAutoLoadClasses()) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      try {
        ReferenceType referenceType = context.getDebugProcess().loadClass(context, e.className(), context.getClassLoader());
        if (referenceType != null) {
          assign(modifier, right, context);
        }
        else {
          throw e;
        }
      }
      catch (InvocationException | InvalidTypeException | IncompatibleThreadStateException | ClassNotLoadedException e1) {
        throw EvaluateExceptionUtil.createEvaluateException(e1);
      }
    }
    catch (InvalidTypeException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  @Override
  public Modifier getModifier() {
    return myLeftEvaluator.getModifier();
  }

  @Override
  public String toString() {
    return myLeftEvaluator + " = " + myRightEvaluator;
  }
}

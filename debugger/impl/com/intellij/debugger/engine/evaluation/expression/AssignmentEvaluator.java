package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 24, 2004
 * Time: 11:10:41 PM
 * To change this template use File | Settings | File Templates.
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

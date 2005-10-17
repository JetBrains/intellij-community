package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.Value;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jul 15, 2003
 * Time: 1:44:35 PM
 * To change this template use Options | File Templates.
 */
public class ExpressionEvaluatorImpl implements ExpressionEvaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator");
  Evaluator myEvaluator;
  Value myValue;

  public ExpressionEvaluatorImpl(Evaluator evaluator) {
    myEvaluator = evaluator;
  }

  //call evaluate before
  public Value getValue() {
    return myValue;
  }

  //call evaluate before
  public Modifier getModifier() {
    return myEvaluator.getModifier();
  }

  // EvaluationContextImpl should be at the same stackFrame as it was in the call to EvaluatorBuilderImpl.build
  public Value evaluate(final EvaluationContext context) throws EvaluateException {
    if (!context.getDebugProcess().isAttached()) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.vm.disconnected"));
    }
    try {
      if (context.getFrameProxy() == null) {
        throw EvaluateExceptionUtil.NULL_STACK_FRAME;
      }

      Object value = myEvaluator.evaluate((EvaluationContextImpl)context);

      if(value != null && !(value instanceof Value)) {
        throw EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", ""));
      }

      myValue = (Value) value;
      return myValue;
    }
    catch (Throwable/*IncompatibleThreadStateException*/ e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      if(e instanceof EvaluateException)
        throw ((EvaluateException)e);
      else
        throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }
}

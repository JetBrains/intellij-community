package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.sun.jdi.Value;

/**
 * User: lex
 * Date: Sep 16, 2003
 * Time: 10:58:26 AM
 */
public abstract class ToStringCommand implements SuspendContextCommand {
  private final EvaluationContext myEvaluationContext;
  private final Value myValue;

  private boolean myIsEvaluated = false;

  protected ToStringCommand(EvaluationContext evaluationContext, Value value) {
    myEvaluationContext = evaluationContext;
    myValue = value;
  }

  public void action() {
    if(myIsEvaluated) return;
    try {
      final String valueAsString = DebuggerUtils.getValueAsString(myEvaluationContext, myValue);
      evaluationResult(valueAsString);
    } 
    catch(final EvaluateException ex) {
      evaluationError(ex.getMessage());
    }
  }

  public void commandCancelled() {
  }

  public void setEvaluated() {
    myIsEvaluated = true;
  }

  public SuspendContext getSuspendContext() {
    return myEvaluationContext.getSuspendContext();
  }

  public abstract void evaluationResult(String message);
  public abstract void evaluationError (String message);

  public Value getValue() {
    return myValue;
  }

  public EvaluationContext getEvaluationContext() {
    return myEvaluationContext;
  }
}


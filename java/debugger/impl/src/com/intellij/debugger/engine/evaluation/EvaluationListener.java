package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.SuspendContextImpl;

import java.util.EventListener;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 3, 2004
 * Time: 7:19:30 PM
 * To change this template use File | Settings | File Templates.
 */
public interface EvaluationListener extends EventListener{
  public void evaluationStarted(SuspendContextImpl context);
  public void evaluationFinished(SuspendContextImpl context);
}

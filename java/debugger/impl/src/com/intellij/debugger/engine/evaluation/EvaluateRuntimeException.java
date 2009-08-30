package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.evaluation.EvaluateException;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jan 14, 2004
 * Time: 2:18:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class EvaluateRuntimeException extends RuntimeException{
  public EvaluateRuntimeException(EvaluateException e) {
    super(e);
  }

  public EvaluateException getCause() {
    return (EvaluateException)super.getCause();
  }
}

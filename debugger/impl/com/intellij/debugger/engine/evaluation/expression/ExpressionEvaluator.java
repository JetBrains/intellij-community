package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.sun.jdi.Value;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jul 15, 2003
 * Time: 1:44:35 PM
 * To change this template use Options | File Templates.
 */
public interface ExpressionEvaluator {
  //call evaluate before
  public Value getValue();

  //call evaluate before
  public Modifier getModifier();

  public Value evaluate(final EvaluationContext context) throws EvaluateException;
}

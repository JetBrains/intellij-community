package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 20, 2004
 * Time: 7:27:10 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class BreakContinueStatementEvaluator implements Evaluator{
  public static Evaluator createBreakEvaluator(final String labelName) {
    return new Evaluator() {
      public Object evaluate(EvaluationContextImpl context) throws BreakException {
        throw new BreakException(labelName);
      }

      public Modifier getModifier() {
        return null;
      }
    };
  }

  public static Evaluator createContinueEvaluator(final String labelName) {
    return new Evaluator() {
      public Object evaluate(EvaluationContextImpl context) throws ContinueException {
        throw new ContinueException(labelName);
      }

      public Modifier getModifier() {
        return null;
      }
    };
  }
}

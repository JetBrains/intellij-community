// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

/**
 * @author lex
 */
public final class BreakContinueStatementEvaluator {
  private BreakContinueStatementEvaluator() {
  }

  public static Evaluator createBreakEvaluator(final String labelName) {
    return new Evaluator() {
      @Override
      public Object evaluate(EvaluationContextImpl context) throws BreakException {
        throw new BreakException(labelName);
      }
    };
  }

  public static Evaluator createContinueEvaluator(final String labelName) {
    return new Evaluator() {
      @Override
      public Object evaluate(EvaluationContextImpl context) throws ContinueException {
        throw new ContinueException(labelName);
      }
    };
  }
}

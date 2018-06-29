// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

/**
 * @author lex
 */
public class BlockStatementEvaluator implements Evaluator {
  protected Evaluator[] myStatements;

  public BlockStatementEvaluator(Evaluator[] statements) {
    myStatements = statements;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object result = context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid();
    for (Evaluator statement : myStatements) {
      result = statement.evaluate(context);
    }
    return result;
  }

  @Override
  public Modifier getModifier() {
    return myStatements.length > 0 ? myStatements[myStatements.length - 1].getModifier() : null;
  }
}

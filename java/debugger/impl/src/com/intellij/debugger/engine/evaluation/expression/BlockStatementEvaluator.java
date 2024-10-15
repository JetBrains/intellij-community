// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

public class BlockStatementEvaluator implements Evaluator {
  protected Evaluator[] myStatements;

  public BlockStatementEvaluator(Evaluator[] statements) {
    myStatements = statements;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object result = context.getVirtualMachineProxy().mirrorOfVoid();
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

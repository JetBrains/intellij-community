// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import org.jetbrains.annotations.NotNull;

public class BlockStatementEvaluator implements ModifiableEvaluator {
  protected Evaluator[] myStatements;

  public BlockStatementEvaluator(Evaluator[] statements) {
    myStatements = statements;
  }

  @Override
  public @NotNull ModifiableValue evaluateModifiable(@NotNull EvaluationContextImpl context) throws EvaluateException {
    ModifiableValue result = new ModifiableValue(context.getVirtualMachineProxy().mirrorOfVoid(), null);
    for (Evaluator statement : myStatements) {
      result = statement.evaluateModifiable(context);
    }
    return result;
  }

  @Override
  public Modifier getModifier() {
    return myStatements.length > 0 ? myStatements[myStatements.length - 1].getModifier() : null;
  }
}

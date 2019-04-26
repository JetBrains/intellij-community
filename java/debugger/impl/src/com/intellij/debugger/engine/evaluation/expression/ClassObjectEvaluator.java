// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class ClassObjectEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

public class ClassObjectEvaluator implements Evaluator {
  private final TypeEvaluator myTypeEvaluator;

  public ClassObjectEvaluator(TypeEvaluator typeEvaluator) {
    myTypeEvaluator = typeEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return myTypeEvaluator.evaluate(context).classObject();
  }
}

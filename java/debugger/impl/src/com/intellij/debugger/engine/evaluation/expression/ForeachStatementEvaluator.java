// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class ForeachStatementEvaluator extends ForStatementEvaluatorBase {
  private final Evaluator myIterationParameterEvaluator;
  private final Evaluator myIterableEvaluator;

  private Evaluator myConditionEvaluator;
  private Evaluator myNextEvaluator;

  private int myArrayLength = -1;
  private int myCurrentIndex = 0;

  private Modifier myModifier;

  public ForeachStatementEvaluator(Evaluator iterationParameterEvaluator,
                               Evaluator iterableEvaluator,
                               Evaluator bodyEvaluator,
                               String labelName) {
    super(labelName, bodyEvaluator);
    myIterationParameterEvaluator = iterationParameterEvaluator;
    myIterableEvaluator = DisableGC.create(iterableEvaluator);
  }

  @Override
  public Modifier getModifier() {
    return myModifier;
  }

  @Override
  protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
    final Object iterable = myIterableEvaluator.evaluate(context);
    if (!(iterable instanceof ObjectReference)) {
      throw new EvaluateException("Unable to do foreach for" + iterable);
    }
    IdentityEvaluator iterableEvaluator = new IdentityEvaluator((Value)iterable);
    if (iterable instanceof ArrayReference) {
      myCurrentIndex = 0;
      myArrayLength = ((ArrayReference)iterable).length();
      myNextEvaluator = new AssignmentEvaluator(myIterationParameterEvaluator,
                                                new Evaluator() {
                                                  @Override
                                                  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
                                                    return ((ArrayReference)iterable).getValue(myCurrentIndex++);
                                                  }
                                                });
    }
    else {
      Object iterator = new MethodEvaluator(iterableEvaluator, null, "iterator", null, new Evaluator[0]).evaluate(context);
      IdentityEvaluator iteratorEvaluator = new IdentityEvaluator((Value)iterator);
      myConditionEvaluator = new MethodEvaluator(iteratorEvaluator, null, "hasNext", null, new Evaluator[0]);
      myNextEvaluator = new AssignmentEvaluator(myIterationParameterEvaluator,
                                                new MethodEvaluator(iteratorEvaluator, null, "next", null, new Evaluator[0]));
    }
    return value;
  }

  private boolean isArray() {
    return myArrayLength > -1;
  }

  @Override
  protected Object evaluateCondition(EvaluationContextImpl context) throws EvaluateException {
    if (isArray()) {
      return myCurrentIndex < myArrayLength;
    }
    else {
      Object res = myConditionEvaluator.evaluate(context);
      myModifier = myConditionEvaluator.getModifier();
      return res;
    }
  }

  @Override
  protected void evaluateBody(EvaluationContextImpl context) throws EvaluateException {
    myNextEvaluator.evaluate(context);
    super.evaluateBody(context);
  }
}

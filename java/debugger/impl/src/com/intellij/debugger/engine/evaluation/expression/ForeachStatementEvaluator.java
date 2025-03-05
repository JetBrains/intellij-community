// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.psi.CommonClassNames;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class ForeachStatementEvaluator implements Evaluator {
  private final Evaluator myIterationParameterEvaluator;
  private final Evaluator myIterableEvaluator;
  private final Evaluator myBodyEvaluator;
  private final String myLabelName;

  public ForeachStatementEvaluator(Evaluator iterationParameterEvaluator,
                                   Evaluator iterableEvaluator,
                                   Evaluator bodyEvaluator,
                                   String labelName) {
    myIterationParameterEvaluator = iterationParameterEvaluator;
    myIterableEvaluator = DisableGC.create(iterableEvaluator);
    myBodyEvaluator = bodyEvaluator;
    myLabelName = labelName;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object iterable = myIterableEvaluator.evaluate(context);
    if (!(iterable instanceof ObjectReference)) {
      throw new EvaluateException("Unable to do foreach for" + iterable);
    }

    if (iterable instanceof ArrayReference) {
      return new ForStatementEvaluatorBase(myLabelName, myBodyEvaluator) {
        private int myCurrentIndex = 0;
        private int myArrayLength = -1;
        private Evaluator myNextEvaluator;

        @Override
        protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
          myArrayLength = ((ArrayReference)iterable).length();
          myNextEvaluator = new AssignmentEvaluator(myIterationParameterEvaluator,
                                                    new Evaluator() {
                                                      @Override
                                                      public Object evaluate(EvaluationContextImpl context) {
                                                        return ((ArrayReference)iterable).getValue(myCurrentIndex++);
                                                      }
                                                    });
          return value;
        }

        @Override
        protected Object evaluateCondition(EvaluationContextImpl context) {
          return myCurrentIndex < myArrayLength;
        }

        @Override
        protected void evaluateBody(EvaluationContextImpl context) throws EvaluateException {
          myNextEvaluator.evaluate(context);
          super.evaluateBody(context);
        }
      }.evaluate(context);
    }
    else {
      return new ForStatementEvaluatorBase(myLabelName, myBodyEvaluator) {
        private MethodEvaluator myConditionEvaluator;
        private AssignmentEvaluator myNextEvaluator;

        @Override
        protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
          Object iterator = new MethodEvaluator(new IdentityEvaluator((Value)iterable),
                                                JVMNameUtil.getJVMRawText(CommonClassNames.JAVA_LANG_ITERABLE),
                                                "iterator", null,
                                                new Evaluator[0]).evaluate(context);
          IdentityEvaluator iteratorEvaluator = new IdentityEvaluator((Value)iterator);
          myConditionEvaluator = new MethodEvaluator(iteratorEvaluator,
                                                     JVMNameUtil.getJVMRawText(CommonClassNames.JAVA_UTIL_ITERATOR),
                                                     "hasNext", null,
                                                     new Evaluator[0]);
          myNextEvaluator = new AssignmentEvaluator(myIterationParameterEvaluator,
                                                    new MethodEvaluator(iteratorEvaluator,
                                                                        JVMNameUtil.getJVMRawText(CommonClassNames.JAVA_UTIL_ITERATOR),
                                                                        "next", null,
                                                                        new Evaluator[0]));
          return value;
        }

        @Override
        protected Object evaluateCondition(EvaluationContextImpl context) throws EvaluateException {
          return myConditionEvaluator.evaluate(context);
        }

        @Override
        protected void evaluateBody(EvaluationContextImpl context) throws EvaluateException {
          myNextEvaluator.evaluate(context);
          super.evaluateBody(context);
        }
      }.evaluate(context);
    }
  }
}

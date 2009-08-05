/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiVariable;

/**
 * @author Gregory.Shrago
 */
public class ValuableDataFlowRunner extends AnnotationsAwareDataFlowRunner {

  protected DfaMemoryState createMemoryState() {
    return new MyDfaMemoryState(getFactory());
  }

  protected ControlFlowAnalyzer createControlFlowAnalyzer() {
    final ControlFlowAnalyzer analyzer = super.createControlFlowAnalyzer();
    analyzer.setHonorRuntimeExceptions(false);
    return analyzer;
  }

  static class MyDfaMemoryState extends DfaMemoryStateImpl {
    private MyDfaMemoryState(final DfaValueFactory factory) {
      super(factory);
    }

    protected DfaMemoryStateImpl createNew() {
      return new MyDfaMemoryState(getFactory());
    }

    protected DfaVariableState createVariableState(final PsiVariable psiVariable) {
      return new ValuableDfaVariableState(psiVariable);
    }

  }

  static class ValuableDfaVariableState extends DfaVariableState {
    DfaValue myValue;
    PsiExpression myExpression;

    private ValuableDfaVariableState(final PsiVariable psiVariable) {
      super(psiVariable);
    }

    protected ValuableDfaVariableState(final ValuableDfaVariableState state) {
      super(state);
      myExpression = state.myExpression;
    }

    public void setValue(final DfaValue value) {
      myValue = value;
    }

    public DfaValue getValue() {
      return myValue;
    }

    protected Object clone() throws CloneNotSupportedException {
      return new ValuableDfaVariableState(this);
    }
  }
}

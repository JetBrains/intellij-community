/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public class ValuableDataFlowRunner extends DataFlowRunner {
  public ValuableDataFlowRunner(final PsiExpression context) {
    super(new MyInstructionFactory(context));
  }

  protected DfaMemoryState createMemoryState() {
    return new MyDfaMemoryState(getFactory());
  }

  protected ControlFlowAnalyzer createControlFlowAnalyzer() {
    final ControlFlowAnalyzer analyzer = super.createControlFlowAnalyzer();
    analyzer.setHonorRuntimeExceptions(false);
    return analyzer;
  }

  @NotNull
  public Collection<PsiExpression> getPossibleVariableValues(final PsiVariable psiVariable) {
    Collection<PsiExpression> psiExpressions = ((MyInstructionFactory)getInstructionFactory()).myValues.get(psiVariable);
    return psiExpressions == null? Collections.<PsiExpression>emptyList() : psiExpressions;
  }

  @NotNull
  public MultiValuesMap<PsiVariable, PsiExpression> getAllVariableValues() {
    return ((MyInstructionFactory)getInstructionFactory()).myValues;
  }

  public static class MyInstructionFactory extends InstructionFactory {
    private final MultiValuesMap<PsiVariable, PsiExpression> myValues = new MultiValuesMap<PsiVariable, PsiExpression>();

    private final PsiExpression myContext;

    public MyInstructionFactory(final PsiExpression context) {
      myContext = context;
    }

    public PushInstruction createPushInstruction(final DfaValue value, final PsiExpression expression) {
      return new PushInstruction(value){
        public DfaInstructionState[] apply(final DataFlowRunner runner, final DfaMemoryState memState) {
          if (myContext == expression) {
            final Map<DfaVariableValue,DfaVariableState> map = ((MyDfaMemoryState)memState).getVariableStates();
            for (DfaVariableValue value : map.keySet()) {
              MyDfaVariableState state = (MyDfaVariableState)map.get(value);
              final PsiExpression psiExpression = state.myExpression;
              if (psiExpression != null) {
                myValues.put(value.getPsiVariable(), psiExpression);
              }
            }
          }
          return super.apply(runner, memState);
        }
      };
    }

    public AssignInstruction createAssignInstruction(final PsiExpression RExpression) {
      return new AssignInstruction(RExpression) {
        public DfaInstructionState[] apply(final DataFlowRunner runner, final DfaMemoryState memState) {
          final DfaInstructionState[] states = super.apply(runner, memState);
          final DfaValue value = memState.peek();
          if (value instanceof DfaVariableValue) {
            final MyDfaVariableState state = (MyDfaVariableState)((MyDfaMemoryState)memState).getVariableState((DfaVariableValue)value);
            if (state.myExpression == null) {
              state.myExpression = getRExpression();
            }
          }
          return states;
        }
      };
    }
  }

  private static class MyDfaMemoryState extends DfaMemoryStateImpl {
    public MyDfaMemoryState(final DfaValueFactory factory) {
      super(factory);
    }

    protected DfaMemoryStateImpl createNew() {
      return new MyDfaMemoryState(getFactory());
    }

    protected DfaVariableState createVariableState(final PsiVariable psiVariable) {
      return new MyDfaVariableState(psiVariable);
    }

  }

  private static class MyDfaVariableState extends DfaVariableState {
    DfaValue myValue;
    PsiExpression myExpression;

    public MyDfaVariableState(final PsiVariable psiVariable) {
      super(psiVariable);
    }

    protected MyDfaVariableState(final MyDfaVariableState state) {
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
      return new MyDfaVariableState(this);
    }
  }
}

/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
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
      return new PushInstruction(value, expression){
        public DfaInstructionState[] apply(final DataFlowRunner runner, final DfaMemoryState memState) {
          if (myContext == expression) {
            final Map<DfaVariableValue,DfaVariableState> map = ((MyDfaMemoryState)memState).getVariableStates();
            for (Map.Entry<DfaVariableValue, DfaVariableState> entry : map.entrySet()) {
              MyDfaVariableState state = (MyDfaVariableState)entry.getValue();
              DfaVariableValue variableValue = entry.getKey();
              final PsiExpression psiExpression = state.myExpression;
              if (psiExpression != null) {
                myValues.put(variableValue.getPsiVariable(), psiExpression);
              }
            }
          }
          return super.apply(runner, memState);
        }
      };
    }

    public AssignInstruction createAssignInstruction(final PsiExpression rExpression) {
      return new AssignInstruction(rExpression) {
        public DfaInstructionState[] apply(final DataFlowRunner runner, final DfaMemoryState memState) {
          final Instruction nextInstruction = runner.getInstruction(getIndex() + 1);

          final DfaValue dfaSource = memState.pop();
          final DfaValue dfaDest = memState.pop();

          if (dfaDest instanceof DfaVariableValue) {
            DfaVariableValue var = (DfaVariableValue)dfaDest;
            final PsiExpression rightValue = getRExpression();
            final PsiElement parent = rightValue == null ? null : rightValue.getParent();
            final IElementType type = parent instanceof PsiAssignmentExpression ? ((PsiAssignmentExpression)parent).getOperationTokenType() : JavaTokenType.EQ;
            // store current value - to use in case of '+='
            final PsiExpression prevValue = ((MyDfaVariableState)((MyDfaMemoryState)memState).getVariableState(var)).myExpression;
            memState.setVarValue(var, dfaSource);
            // state may have been changed so re-retrieve it
            final MyDfaVariableState curState = (MyDfaVariableState)((MyDfaMemoryState)memState).getVariableState(var);
            final PsiExpression curValue = curState.myExpression;
            final PsiExpression nextValue;
            if (type == JavaTokenType.PLUSEQ && prevValue != null) {
              PsiExpression tmpExpression;
              try {
                tmpExpression = JavaPsiFacade.getElementFactory(myContext.getProject())
                  .createExpressionFromText(prevValue.getText() + "+" + rightValue.getText(), rightValue);
              }
              catch (Exception e) {
                tmpExpression = curValue == null ? rightValue : curValue;
              }
              nextValue = tmpExpression;
            }
            else {
              nextValue = curValue == null ? rightValue : curValue;
            }
            curState.myExpression = nextValue;
          }
          memState.push(dfaDest);
          return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
        }
      };
    }
  }

  private static class MyDfaMemoryState extends DfaMemoryStateImpl {
    private MyDfaMemoryState(final DfaValueFactory factory) {
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

    private MyDfaVariableState(final PsiVariable psiVariable) {
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

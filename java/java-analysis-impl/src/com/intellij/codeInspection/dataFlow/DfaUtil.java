/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Gregory.Shrago
 */
public class DfaUtil {
  private static final Key<CachedValue<MultiValuesMap<PsiVariable, PsiExpression>>> DFA_VARIABLE_INFO_KEY = Key.create("DFA_VARIABLE_INFO_KEY");

  private DfaUtil() {
  }

  private static final MultiValuesMap<PsiVariable, PsiExpression> TOO_COMPLEX = new MultiValuesMap<PsiVariable, PsiExpression>();
  @Nullable("null means DFA analysis has failed (too complex to analyze)")
  public static Collection<PsiExpression> getCachedVariableValues(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    if (variable == null || context == null) return Collections.emptyList();

    CachedValue<MultiValuesMap<PsiVariable, PsiExpression>> cachedValue = context.getUserData(DFA_VARIABLE_INFO_KEY);
    if (cachedValue == null) {
      final PsiElement codeBlock = DfaPsiUtil.getEnclosingCodeBlock(variable, context);
      cachedValue = CachedValuesManager.getManager(context.getProject()).createCachedValue(new CachedValueProvider<MultiValuesMap<PsiVariable, PsiExpression>>() {
        @Override
        public Result<MultiValuesMap<PsiVariable, PsiExpression>> compute() {
          final MultiValuesMap<PsiVariable, PsiExpression> result;
          if (codeBlock == null) {
            result = null;
          }
          else {
            final ValuableInstructionVisitor visitor = new ValuableInstructionVisitor(context);
            RunnerResult runnerResult = new ValuableDataFlowRunner(codeBlock).analyzeMethod(codeBlock, visitor);
            if (runnerResult == RunnerResult.OK) {
              result = visitor.myValues;
            }
            else {
              result = TOO_COMPLEX;
            }
          }
          return new Result<MultiValuesMap<PsiVariable, PsiExpression>>(result, variable);
        }
      }, false);
      context.putUserData(DFA_VARIABLE_INFO_KEY, cachedValue);
    }
    final MultiValuesMap<PsiVariable, PsiExpression> value = cachedValue.getValue();
    if (value == TOO_COMPLEX) return null;
    final Collection<PsiExpression> expressions = value == null ? null : value.get(variable);
    return expressions == null ? Collections.<PsiExpression>emptyList() : expressions;
  }

  @NotNull
  public static Nullness checkNullness(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    if (variable == null || context == null) return Nullness.UNKNOWN;

    final PsiElement codeBlock = DfaPsiUtil.getEnclosingCodeBlock(variable, context);
    if (codeBlock == null) {
      return Nullness.UNKNOWN;
    }
    final ValuableInstructionVisitor visitor = new ValuableInstructionVisitor(context);
    RunnerResult result = new ValuableDataFlowRunner(codeBlock).analyzeMethod(codeBlock, visitor);
    if (result != RunnerResult.OK) {
      return Nullness.UNKNOWN;
    }
    if (visitor.myNulls.contains(variable) && !visitor.myNotNulls.contains(variable)) return Nullness.NULLABLE;
    if (visitor.myNotNulls.contains(variable) && !visitor.myNulls.contains(variable)) return Nullness.NOT_NULL;
    return Nullness.UNKNOWN;
  }

  @NotNull
  public static Collection<? extends PsiElement> getPossibleInitializationElements(final PsiElement qualifierExpression) {
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      return Collections.singletonList(qualifierExpression);
    }
    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement targetElement = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (!(targetElement instanceof PsiVariable)) {
        return Collections.emptyList();
      }
      final Collection<? extends PsiElement> variableValues = getCachedVariableValues((PsiVariable)targetElement, qualifierExpression);
      if (variableValues == null || variableValues.isEmpty()) {
        return DfaPsiUtil.getVariableAssignmentsInFile((PsiVariable)targetElement, false, qualifierExpression);
      }
      return variableValues;
    }
    if (qualifierExpression instanceof PsiLiteralExpression) {
      return Collections.singletonList(qualifierExpression);
    }
    return Collections.emptyList();
  }

  private static class ValuableInstructionVisitor extends StandardInstructionVisitor {
    final MultiValuesMap<PsiVariable, PsiExpression> myValues = new MultiValuesMap<PsiVariable, PsiExpression>(true);
    final Set<PsiVariable> myNulls = new THashSet<PsiVariable>();
    final Set<PsiVariable> myNotNulls = new THashSet<PsiVariable>();
    private final PsiElement myContext;

    public ValuableInstructionVisitor(@NotNull PsiElement context) {
      myContext = context;
    }

    @Override
    public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myContext == instruction.getPlace()) {
        final Map<DfaVariableValue,DfaVariableState> map = ((ValuableDataFlowRunner.MyDfaMemoryState)memState).getVariableStates();
        for (Map.Entry<DfaVariableValue, DfaVariableState> entry : map.entrySet()) {
          ValuableDataFlowRunner.ValuableDfaVariableState state = (ValuableDataFlowRunner.ValuableDfaVariableState)entry.getValue();
          DfaVariableValue variableValue = entry.getKey();
          final PsiExpression psiExpression = state.myExpression;
          if (psiExpression != null && variableValue.getQualifier() == null) {
            PsiModifierListOwner element = variableValue.getPsiVariable();
            if (element instanceof PsiVariable) {
              myValues.put((PsiVariable)element, psiExpression);
            }
          }
        }
        DfaValue value = instruction.getValue();
        if (value instanceof DfaVariableValue && ((DfaVariableValue)value).getQualifier() == null) {
          PsiModifierListOwner element = ((DfaVariableValue)value).getPsiVariable();
          if (element instanceof PsiVariable) {
            if (memState.isNotNull((DfaVariableValue)value)) {
              myNotNulls.add((PsiVariable)element);
            }
            if (memState.isNull(value)) {
              myNulls.add((PsiVariable)element);
            }
          }
        }
      }
      return super.visitPush(instruction, runner, memState);
    }

    @Override
    public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState _memState) {
      final Instruction nextInstruction = runner.getInstruction(instruction.getIndex() + 1);

      ValuableDataFlowRunner.MyDfaMemoryState memState = (ValuableDataFlowRunner.MyDfaMemoryState)_memState;
      final DfaValue dfaSource = memState.pop();
      final DfaValue dfaDest = memState.pop();

      if (dfaDest instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)dfaDest;
        final PsiExpression rightValue = instruction.getRExpression();
        final PsiElement parent = rightValue == null ? null : rightValue.getParent();
        final IElementType type = parent instanceof PsiAssignmentExpression
                                  ? ((PsiAssignmentExpression)parent).getOperationTokenType() : JavaTokenType.EQ;
        // store current value - to use in case of '+='
        final PsiExpression prevValue = ((ValuableDataFlowRunner.ValuableDfaVariableState)memState.getVariableState(var)).myExpression;
        memState.setVarValue(var, dfaSource);
        // state may have been changed so re-retrieve it
        final ValuableDataFlowRunner.ValuableDfaVariableState curState = (ValuableDataFlowRunner.ValuableDfaVariableState)memState.getVariableState(var);
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
        memState.setVariableState(var, curState.withExpression(nextValue));
      }
      memState.push(dfaDest);
      return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
    }
  }
}

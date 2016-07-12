/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Gregory.Shrago
 */
public class DfaUtil {

  @Nullable("null means DFA analysis has failed (too complex to analyze)")
  public static Collection<PsiExpression> getCachedVariableValues(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    if (variable == null || context == null) return Collections.emptyList();

    final PsiElement codeBlock = DfaPsiUtil.getEnclosingCodeBlock(variable, context);
    if (codeBlock == null) return Collections.emptyList();

    final Map<PsiElement, ValuableInstructionVisitor.PlaceResult> value = getCachedPlaceResults(codeBlock);
    if (value == null) return null;

    ValuableInstructionVisitor.PlaceResult placeResult = value.get(context);
    final Collection<FList<PsiExpression>> concatenations = placeResult == null ? null : placeResult.myValues.get(variable);
    if (concatenations != null) {
      return ContainerUtil.map(concatenations, expressions -> concatenateExpressions(expressions));
    }
    return Collections.emptyList();
  }

  @Nullable("null means DFA analysis has failed (too complex to analyze)")
  private static Map<PsiElement, ValuableInstructionVisitor.PlaceResult> getCachedPlaceResults(@NotNull final PsiElement codeBlock) {
    return CachedValuesManager.getCachedValue(codeBlock, () -> {
      final ValuableInstructionVisitor visitor = new ValuableInstructionVisitor();
      RunnerResult runnerResult = new ValuableDataFlowRunner().analyzeMethod(codeBlock, visitor);
      return CachedValueProvider.Result.create(runnerResult == RunnerResult.OK ? visitor.myResults : null, codeBlock);
    });
  }

  @NotNull
  public static Nullness checkNullness(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    if (variable == null || context == null) return Nullness.UNKNOWN;

    final PsiElement codeBlock = DfaPsiUtil.getEnclosingCodeBlock(variable, context);
    Map<PsiElement, ValuableInstructionVisitor.PlaceResult> results = codeBlock == null ? null : getCachedPlaceResults(codeBlock);
    ValuableInstructionVisitor.PlaceResult placeResult = results == null ? null : results.get(context);
    if (placeResult == null) {
      return Nullness.UNKNOWN;
    }
    if (placeResult.myNulls.contains(variable) && !placeResult.myNotNulls.contains(variable)) return Nullness.NULLABLE;
    if (placeResult.myNotNulls.contains(variable) && !placeResult.myNulls.contains(variable)) return Nullness.NOT_NULL;
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

  @Nullable
  static PsiElement getClosureInside(Instruction instruction) {
    if (instruction instanceof MethodCallInstruction) {
      PsiCall anchor = ((MethodCallInstruction)instruction).getCallExpression();
      if (anchor instanceof PsiNewExpression) {
        return ((PsiNewExpression)anchor).getAnonymousClass();
      }
    }
    else if (instruction instanceof LambdaInstruction) {
      return ((LambdaInstruction)instruction).getLambdaExpression();
    }
    else if (instruction instanceof EmptyInstruction) {
      PsiElement anchor = ((EmptyInstruction)instruction).getAnchor();
      if (anchor instanceof PsiClass) {
        return anchor;
      }
    }
    return null;
  }

  @NotNull
  public static Nullness inferMethodNullity(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null || PsiUtil.resolveClassInType(method.getReturnType()) == null) {
      return Nullness.UNKNOWN;
    }

    final AtomicBoolean hasNulls = new AtomicBoolean();
    final AtomicBoolean hasNotNulls = new AtomicBoolean();
    final AtomicBoolean hasUnknowns = new AtomicBoolean();

    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner();
    final RunnerResult rc = dfaRunner.analyzeMethod(body, new StandardInstructionVisitor() {
      @Override
      public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                         DataFlowRunner runner,
                                                         DfaMemoryState memState) {
        DfaValue returned = memState.peek();
        if (memState.isNull(returned)) {
          hasNulls.set(true);
        }
        else if (memState.isNotNull(returned)) {
          hasNotNulls.set(true);
        }
        else {
          hasUnknowns.set(true);
        }
        return super.visitCheckReturnValue(instruction, runner, memState);
      }
    });

    if (rc == RunnerResult.OK) {
      if (hasNulls.get()) {
        return InferenceFromSourceUtil.suppressNullable(method) ? Nullness.UNKNOWN : Nullness.NULLABLE;
      }
      if (hasNotNulls.get() && !hasUnknowns.get()) {
        return Nullness.NOT_NULL;
      }
    }

    return Nullness.UNKNOWN;
  }

  private static class ValuableInstructionVisitor extends StandardInstructionVisitor {
    final Map<PsiElement, PlaceResult> myResults = ContainerUtil.newHashMap();

    static class PlaceResult {
      final MultiValuesMap<PsiVariable, FList<PsiExpression>> myValues = new MultiValuesMap<PsiVariable, FList<PsiExpression>>(true);
      final Set<PsiVariable> myNulls = new THashSet<PsiVariable>();
      final Set<PsiVariable> myNotNulls = new THashSet<PsiVariable>();
    }

    @Override
    public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      PsiExpression place = instruction.getPlace();
      if (place != null) {
        PlaceResult result = myResults.get(place);
        if (result == null) {
          myResults.put(place, result = new PlaceResult());
        }
        final Map<DfaVariableValue,DfaVariableState> map = ((ValuableDataFlowRunner.MyDfaMemoryState)memState).getVariableStates();
        for (Map.Entry<DfaVariableValue, DfaVariableState> entry : map.entrySet()) {
          ValuableDataFlowRunner.ValuableDfaVariableState state = (ValuableDataFlowRunner.ValuableDfaVariableState)entry.getValue();
          DfaVariableValue variableValue = entry.getKey();
          final FList<PsiExpression> concatenation = state.myConcatenation;
          if (!concatenation.isEmpty() && variableValue.getQualifier() == null) {
            PsiModifierListOwner element = variableValue.getPsiVariable();
            if (element instanceof PsiVariable) {
              result.myValues.put((PsiVariable)element, concatenation);
            }
          }
        }
        DfaValue value = instruction.getValue();
        if (value instanceof DfaVariableValue && ((DfaVariableValue)value).getQualifier() == null) {
          PsiModifierListOwner element = ((DfaVariableValue)value).getPsiVariable();
          if (element instanceof PsiVariable) {
            if (memState.isNotNull(value)) {
              result.myNotNulls.add((PsiVariable)element);
            }
            if (memState.isNull(value)) {
              result.myNulls.add((PsiVariable)element);
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
        final FList<PsiExpression> prevValue = ((ValuableDataFlowRunner.ValuableDfaVariableState)memState.getVariableState(var)).myConcatenation;
        memState.setVarValue(var, dfaSource);
        // state may have been changed so re-retrieve it
        final ValuableDataFlowRunner.ValuableDfaVariableState curState = (ValuableDataFlowRunner.ValuableDfaVariableState)memState.getVariableState(var);
        final FList<PsiExpression> curValue = curState.myConcatenation;
        final FList<PsiExpression> nextValue;
        if (type == JavaTokenType.PLUSEQ && !prevValue.isEmpty() && rightValue != null) {
          nextValue = prevValue.prepend(rightValue);
        }
        else {
          nextValue = curValue.isEmpty() && rightValue != null ? curValue.prepend(rightValue) : curValue;
        }
        memState.setVariableState(var, curState.withExpression(nextValue));
      }
      memState.push(dfaDest);
      return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
    }
  }

  private static PsiExpression concatenateExpressions(FList<PsiExpression> concatenation) {
    if (concatenation.size() == 1) {
      return concatenation.getHead();
    }
    String text = StringUtil
      .join(ContainerUtil.reverse(new ArrayList<PsiExpression>(concatenation)), expression -> expression.getText(), "+");
    try {
      return JavaPsiFacade.getElementFactory(concatenation.getHead().getProject()).createExpressionFromText(text, concatenation.getHead());
    }
    catch (IncorrectOperationException e) {
      return concatenation.getHead();
    }
  }
}

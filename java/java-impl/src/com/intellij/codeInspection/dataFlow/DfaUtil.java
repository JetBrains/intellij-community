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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class DfaUtil {
  private static final Key<CachedValue<MultiValuesMap<PsiVariable, PsiExpression>>> DFA_VARIABLE_INFO_KEY = Key.create("DFA_VARIABLE_INFO_KEY");

  private DfaUtil() {
  }

  public static Collection<PsiExpression> getCachedVariableValues(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    if (variable == null || context == null) return Collections.emptyList();

    CachedValue<MultiValuesMap<PsiVariable, PsiExpression>> cachedValue = context.getUserData(DFA_VARIABLE_INFO_KEY);
    if (cachedValue == null) {
      final PsiElement codeBlock = getEnclosingCodeBlock(variable, context);
      cachedValue = context.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<MultiValuesMap<PsiVariable, PsiExpression>>() {
        public Result<MultiValuesMap<PsiVariable, PsiExpression>> compute() {
          final MultiValuesMap<PsiVariable, PsiExpression> result;
          if (codeBlock == null) {
            result = null;
          }
          else {
            final ValuableInstructionVisitor visitor = new ValuableInstructionVisitor(context);
            if (new ValuableDataFlowRunner().analyzeMethod(codeBlock, visitor) == RunnerResult.OK) {
              result = visitor.myValues;
            }
            else {
              result = null;
            }
          }
          return new Result<MultiValuesMap<PsiVariable, PsiExpression>>(result, codeBlock);
        }
      }, false);
      context.putUserData(DFA_VARIABLE_INFO_KEY, cachedValue);
    }
    final MultiValuesMap<PsiVariable, PsiExpression> value = cachedValue.getValue();
    final Collection<PsiExpression> expressions = value == null ? null : value.get(variable);
    return expressions == null ? Collections.<PsiExpression>emptyList() : expressions;
  }

  public static enum Nullness {
    NOT_NULL,NULL,UNKNOWN
  }
  // TRUE->not null, FALSE->null, null->unknown
  @NotNull
  public static Nullness checkNullness(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    if (variable == null || context == null) return Nullness.UNKNOWN;

    final PsiElement codeBlock = getEnclosingCodeBlock(variable, context);
    if (codeBlock == null) {
      return Nullness.UNKNOWN;
    }
    final ValuableInstructionVisitor visitor = new ValuableInstructionVisitor(context);
    RunnerResult result = new ValuableDataFlowRunner().analyzeMethod(codeBlock, visitor);
    if (result != RunnerResult.OK) {
      return Nullness.UNKNOWN;
    }
    if (visitor.myNulls.contains(variable) && !visitor.myNotNulls.contains(variable)) return Nullness.NULL;
    if (visitor.myNotNulls.contains(variable) && !visitor.myNulls.contains(variable)) return Nullness.NOT_NULL;
    return Nullness.UNKNOWN;
  }

  @Nullable
  public static PsiCodeBlock getTopmostBlockInSameClass(@NotNull PsiElement position) {
    PsiCodeBlock block = PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, false, PsiMember.class, PsiFile.class);
    if (block == null) {
      return null;
    }

    PsiCodeBlock lastBlock = block;
    while (true) {
      block = PsiTreeUtil.getParentOfType(block, PsiCodeBlock.class, true, PsiMember.class, PsiFile.class);
      if (block == null) {
        return lastBlock;
      }
      lastBlock = block;
    }
  }

  private static PsiElement getEnclosingCodeBlock(final PsiVariable variable, final PsiElement context) {
    PsiElement codeBlock;
    if (variable instanceof PsiParameter) {
      codeBlock = ((PsiParameter)variable).getDeclarationScope();
      if (codeBlock instanceof PsiMethod) {
        codeBlock = ((PsiMethod)codeBlock).getBody();
      }
    }
    else if (variable instanceof PsiLocalVariable) {
      codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    }
    else {
      codeBlock = PsiTreeUtil.getParentOfType(context, PsiCodeBlock.class);
    }
    while (codeBlock != null) {
      PsiAnonymousClass anon = PsiTreeUtil.getParentOfType(codeBlock, PsiAnonymousClass.class);
      if (anon == null) break;
      codeBlock = PsiTreeUtil.getParentOfType(anon, PsiCodeBlock.class);
    }
    return codeBlock;
  }

  public static Collection<? extends PsiElement> getPossibleInitializationElements(final PsiElement qualifierExpression) {
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      return Collections.singletonList(qualifierExpression);
    }
    else if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement targetElement = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (targetElement instanceof PsiVariable) {
        final Collection<? extends PsiElement> variableValues = getCachedVariableValues((PsiVariable)targetElement, (PsiExpression)qualifierExpression);
        if (variableValues.isEmpty() && targetElement instanceof PsiField) {
          return getVariableAssignmentsInFile((PsiVariable)targetElement, false);
        }
        return variableValues;
      }
    }
    else if (qualifierExpression instanceof PsiLiteralExpression) {
      return Collections.singletonList(qualifierExpression);
    }
    return Collections.emptyList();
  }

  public static Collection<PsiExpression> getVariableAssignmentsInFile(final PsiVariable psiVariable, final boolean literalsOnly) {
    final Ref<Boolean> modificationRef = Ref.create(Boolean.FALSE);
    final List<PsiExpression> list = ContainerUtil.mapNotNull(
      ReferencesSearch.search(psiVariable, new LocalSearchScope(new PsiElement[] {psiVariable.getContainingFile()}, null, true)).findAll(),
      new NullableFunction<PsiReference, PsiExpression>() {
        public PsiExpression fun(final PsiReference psiReference) {
          if (modificationRef.get()) return null;
          final PsiElement parent = psiReference.getElement().getParent();
          if (parent instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
            final IElementType operation = assignmentExpression.getOperationTokenType();
            if (assignmentExpression.getLExpression() == psiReference) {
              if (JavaTokenType.EQ.equals(operation)) {
                if (!literalsOnly || allOperandsAreLiterals(assignmentExpression.getRExpression())) {
                  return assignmentExpression.getRExpression();
                }
                else {
                  modificationRef.set(Boolean.TRUE);
                }
              }
              else if (JavaTokenType.PLUSEQ.equals(operation)) {
                modificationRef.set(Boolean.TRUE);
              }
            }
          }
          return null;
        }
      });
    if (modificationRef.get()) return Collections.emptyList();
    if (!literalsOnly || allOperandsAreLiterals(psiVariable.getInitializer())) {
      ContainerUtil.addIfNotNull(psiVariable.getInitializer(), list);
    }
    return list;
  }

  public static boolean allOperandsAreLiterals(@Nullable final PsiExpression expression) {
    if (expression == null) return false;
    if (expression instanceof PsiLiteralExpression) return true;
    if (expression instanceof PsiBinaryExpression) {
      final LinkedList<PsiExpression> stack = new LinkedList<PsiExpression>();
      stack.add(expression);
      while (!stack.isEmpty()) {
        final PsiExpression psiExpression = stack.removeFirst();
        if (psiExpression instanceof PsiBinaryExpression) {
          final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)psiExpression;
          stack.addLast(binaryExpression.getLOperand());
          final PsiExpression right = binaryExpression.getROperand();
          if (right != null) {
            stack.addLast(right);
          }
        }
        else if (!(psiExpression instanceof PsiLiteralExpression)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static class ValuableInstructionVisitor extends StandardInstructionVisitor {
    final MultiValuesMap<PsiVariable, PsiExpression> myValues = new MultiValuesMap<PsiVariable, PsiExpression>(true);
    final Set<PsiVariable> myNulls = new THashSet<PsiVariable>();
    final Set<PsiVariable> myNotNulls = new THashSet<PsiVariable>();
    private final PsiElement myContext;

    public ValuableInstructionVisitor(PsiElement context) {
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
          if (psiExpression != null) {
            myValues.put(variableValue.getPsiVariable(), psiExpression);
          }
        }
        DfaValue value = instruction.getValue();
        if (value instanceof DfaVariableValue) {
          if (memState.isNotNull((DfaVariableValue)value)) {
            myNotNulls.add(((DfaVariableValue)value).getPsiVariable());
          }
          if (memState.isNull(value)) {
            myNulls.add(((DfaVariableValue)value).getPsiVariable());
          }
        }
      }
      return super.visitPush(instruction, runner, memState);
    }

    @Override
    public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      final Instruction nextInstruction = runner.getInstruction(instruction.getIndex() + 1);

      final DfaValue dfaSource = memState.pop();
      final DfaValue dfaDest = memState.pop();

      if (dfaDest instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)dfaDest;
        final PsiExpression rightValue = instruction.getRExpression();
        final PsiElement parent = rightValue == null ? null : rightValue.getParent();
        final IElementType type = parent instanceof PsiAssignmentExpression
                                  ? ((PsiAssignmentExpression)parent).getOperationTokenType() : JavaTokenType.EQ;
        // store current value - to use in case of '+='
        final PsiExpression prevValue = ((ValuableDataFlowRunner.ValuableDfaVariableState)((ValuableDataFlowRunner.MyDfaMemoryState)memState).getVariableState(var)).myExpression;
        memState.setVarValue(var, dfaSource);
        // state may have been changed so re-retrieve it
        final ValuableDataFlowRunner.ValuableDfaVariableState curState = (ValuableDataFlowRunner.ValuableDfaVariableState)((ValuableDataFlowRunner.MyDfaMemoryState)memState).getVariableState(var);
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
  }
}

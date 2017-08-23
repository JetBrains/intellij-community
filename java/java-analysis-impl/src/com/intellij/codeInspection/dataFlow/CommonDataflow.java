/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CommonDataflow {
  static class DataflowResult {
    private final Map<PsiExpression, DfaFactMap> myFacts = new HashMap<>();

    void add(PsiExpression expression, DfaMemoryStateImpl memState) {
      DfaFactMap existing = myFacts.get(expression);
      if(existing == null && myFacts.containsKey(expression)) return; // bottom
      DfaValue value = memState.peek();
      DfaFactMap newMap = memState.getFactMap(value);
      myFacts.put(expression, DfaFactMap.intersect(existing == null ? DfaFactMap.EMPTY : existing, newMap));
    }
  }

  @Contract("null -> null")
  @Nullable
  private static DataflowResult runDFA(@Nullable PsiElement block) {
    if (block == null) return null;
    DataFlowRunner runner = new DataFlowRunner(false, true);
    DfaConstValue fail = runner.getFactory().getConstFactory().getContractFail();
    DataflowResult dfr = new DataflowResult();
    StandardInstructionVisitor visitor = new StandardInstructionVisitor() {
      @Override
      public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
        DfaInstructionState[] states = super.visitPush(instruction, runner, memState);
        PsiExpression place = instruction.getPlace();
        if (place != null) {
          for (DfaInstructionState state : states) {
            dfr.add(place, (DfaMemoryStateImpl)state.getMemoryState());
          }
        }
        return states;
      }

      @Override
      public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction,
                                                   DataFlowRunner runner,
                                                   DfaMemoryState memState) {
        DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
        PsiExpression context = ObjectUtils.tryCast(instruction.getContext(), PsiExpression.class);
        if (context != null) {
          for (DfaInstructionState state : states) {
            DfaValue value = state.getMemoryState().peek();
            if(value != fail) {
              dfr.add(context, (DfaMemoryStateImpl)state.getMemoryState());
            }
          }
        }
        return states;
      }
    };
    RunnerResult result = runner.analyzeMethod(block, visitor);
    return result == RunnerResult.OK ? dfr : null;
  }

  private static DataflowResult getDataflowResult(PsiElement context) {
    PsiMember member = PsiTreeUtil.getParentOfType(context, PsiMember.class);
    if(!(member instanceof PsiMethod)) return null;
    return CachedValuesManager.getCachedValue(member, () -> {
      PsiCodeBlock body = ((PsiMethod)member).getBody();
      DataflowResult result = runDFA(body);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  /**
   * Returns a fact of specific type which is known for given expression or null if fact is not known
   *
   * @param expression expression to get the fact
   * @param type a fact type
   * @param <T> resulting type
   * @return a fact value or null if fact of given type is not known for given expression
   */
  public static <T> T getExpressionFact(PsiExpression expression, DfaFactType<T> type) {
    DataflowResult result = getDataflowResult(expression);
    if (result == null) return null;
    DfaFactMap map = result.myFacts.get(expression);
    return map == null ? null : map.get(type);
  }
}

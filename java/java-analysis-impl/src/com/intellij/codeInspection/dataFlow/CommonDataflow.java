// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.ide.PowerSaveMode;
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
      if(existing != DfaFactMap.EMPTY) {
        DfaValue value = memState.peek();
        DfaFactMap newMap = memState.getFactMap(value);
        if (!Boolean.FALSE.equals(newMap.get(DfaFactType.CAN_BE_NULL)) && memState.isNotNull(value)) {
          newMap = newMap.with(DfaFactType.CAN_BE_NULL, false);
        }
        myFacts.put(expression, existing == null ? newMap : existing.union(newMap));
      }
    }
  }

  @Contract("null -> null")
  @Nullable
  private static DataflowResult runDFA(@Nullable PsiElement block) {
    if (block == null) return null;
    DataFlowRunner runner = new DataFlowRunner(false, !DfaUtil.isInsideConstructorOrInitializer(block));
    DfaConstValue fail = runner.getFactory().getConstFactory().getContractFail();
    DataflowResult dfr = new DataflowResult();
    StandardInstructionVisitor visitor = new StandardInstructionVisitor() {
      @Override
      public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
        DfaInstructionState[] states = super.visitPush(instruction, runner, memState);
        PsiExpression place = instruction.getPlace();
        if (place != null && !instruction.isReferenceWrite()) {
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
    RunnerResult result = runner.analyzeMethodRecursively(block, visitor);
    return result == RunnerResult.OK ? dfr : null;
  }

  private static DataflowResult getDataflowResult(PsiElement context) {
    // Disable common dataflow in powersave mode
    if(PowerSaveMode.isEnabled()) return null;
    PsiMember member = PsiTreeUtil.getParentOfType(context, PsiMember.class);
    if(!(member instanceof PsiMethod) && !(member instanceof PsiField) && !(member instanceof PsiClassInitializer)) return null;
    PsiElement body = member instanceof PsiMethod ? ((PsiMethod)member).getBody() : member.getContainingClass();
    if (body == null) return null;
    return CachedValuesManager.getCachedValue(body, () -> {
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

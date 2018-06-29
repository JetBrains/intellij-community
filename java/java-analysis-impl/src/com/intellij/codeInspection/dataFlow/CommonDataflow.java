// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.dataFlow.DfaUtil.hasImplicitImpureSuperCall;

public class CommonDataflow {
  /**
   * Represents the result of dataflow applied to some code fragment (usually a method)
   */
  public static class DataflowResult {
    private final Map<PsiExpression, DfaFactMap> myFacts = new HashMap<>();

    DataflowResult copy() {
      DataflowResult copy = new DataflowResult();
      copy.myFacts.putAll(myFacts);
      return copy;
    }

    void add(PsiExpression expression, DfaMemoryStateImpl memState, DfaValue value) {
      DfaFactMap existing = myFacts.get(expression);
      if(existing != DfaFactMap.EMPTY) {
        DfaFactMap newMap = memState.getFactMap(value);
        if (!Boolean.FALSE.equals(newMap.get(DfaFactType.CAN_BE_NULL)) && memState.isNotNull(value)) {
          newMap = newMap.with(DfaFactType.CAN_BE_NULL, false);
        }
        myFacts.put(expression, existing == null ? newMap : existing.union(newMap));
      }
    }

    /**
     * Returns true if given expression was visited by dataflow. Note that dataflow usually tracks deparenthesized expressions only,
     * so you should deparenthesize it in advance if necessary.
     *
     * @param expression expression to check, not parenthesized
     * @return true if given expression was visited by dataflow.
     * If false is returned, it's possible that the expression exists in unreachable branch or this expression is not tracked due to
     * the dataflow implementation details.
     */
    public boolean expressionWasAnalyzed(PsiExpression expression) {
      assert !(expression instanceof PsiParenthesizedExpression);
      return myFacts.containsKey(expression);
    }

    /**
     * Returns a fact of specific type which is known for given expression or null if fact is not known
     *
     * @param expression expression to get the fact
     * @param type a fact type
     * @param <T> resulting type
     * @return a fact value or null if fact of given type is not known for given expression
     */
    @Nullable
    public <T> T getExpressionFact(PsiExpression expression, DfaFactType<T> type) {
      DfaFactMap map = this.myFacts.get(expression);
      return map == null ? null : map.get(type);
    }

    /**
     * Returns the fact map which represents all the facts known for given expression
     *
     * @param expression an expression to check
     * @return the fact map which represents all the facts known for given expression; empty map if the expression was
     * analyzed, but no particular facts were inferred; null if the expression was not analyzed.
     */
    @Nullable
    public DfaFactMap getAllFacts(PsiExpression expression) {
      return this.myFacts.get(expression);
    }
  }

  @Contract("null -> null")
  @Nullable
  private static DataflowResult runDFA(@Nullable PsiElement block) {
    if (block == null) return null;
    DataFlowRunner runner = new DataFlowRunner(false, block);
    CommonDataflowVisitor visitor = new CommonDataflowVisitor(runner);
    RunnerResult result = runner.analyzeMethodRecursively(block, visitor);
    if (result != RunnerResult.OK) return null;
    if (!(block instanceof PsiClass)) return visitor.myResult;
    DataflowResult dfr = visitor.myResult.copy();
    List<DfaMemoryState> states = visitor.myEndOfInitializerStates;
    for (PsiMethod method : ((PsiClass)block).getConstructors()) {
      List<DfaMemoryState> initialStates;
      PsiCodeBlock body = method.getBody();
      if (body == null) continue;
      PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
      if (JavaPsiConstructorUtil.isChainedConstructorCall(call) || (call == null && hasImplicitImpureSuperCall((PsiClass)block, method))) {
        initialStates = Collections.singletonList(runner.createMemoryState());
      } else {
        initialStates = StreamEx.of(states).map(DfaMemoryState::createCopy).toList();
      }
      if(runner.analyzeBlockRecursively(body, initialStates, visitor) == RunnerResult.OK) {
        dfr = visitor.myResult.copy();
      } else {
        visitor.myResult = dfr;
      }
    }
    return dfr;
  }

  /**
   * Returns the dataflow result for code fragment which contains given context
   * @param context a context to get the dataflow result
   * @return the dataflow result or null if dataflow cannot be launched for this context (e.g. we are inside too complex method)
   */
  @Nullable
  public static DataflowResult getDataflowResult(PsiExpression context) {
    PsiElement body = DfaUtil.getDataflowContext(context);
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
    return result.getExpressionFact(PsiUtil.skipParenthesizedExprDown(expression), type);
  }

  private static class CommonDataflowVisitor extends StandardInstructionVisitor {
    private DataflowResult myResult;
    private final DfaConstValue myFail;
    private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();

    public CommonDataflowVisitor(DataFlowRunner runner) {
      myFail = runner.getFactory().getConstFactory().getContractFail();
      myResult = new DataflowResult();
    }

    @Override
    public DfaInstructionState[] visitEndOfInitializer(EndOfInitializerInstruction instruction,
                                                       DataFlowRunner runner,
                                                       DfaMemoryState state) {
      if (!instruction.isStatic()) {
        myEndOfInitializerStates.add(state.createCopy());
      }
      return super.visitEndOfInitializer(instruction, runner, state);
    }

    @Override
    public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      DfaInstructionState[] states = super.visitPush(instruction, runner, memState);
      PsiExpression place = instruction.getPlace();
      if (place != null && !instruction.isReferenceWrite()) {
        for (DfaInstructionState state : states) {
          DfaMemoryState afterState = state.getMemoryState();
          myResult.add(place, (DfaMemoryStateImpl)afterState, instruction.getValue());
        }
      }
      return states;
    }

    @Override
    public DfaInstructionState[] visitArrayAccess(ArrayAccessInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      DfaInstructionState[] states = super.visitArrayAccess(instruction, runner, memState);
      PsiArrayAccessExpression anchor = instruction.getExpression();
      for (DfaInstructionState state : states) {
        DfaMemoryState afterState = state.getMemoryState();
        myResult.add(anchor, (DfaMemoryStateImpl)afterState, afterState.peek());
      }
      return states;
    }

    @Override
    public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      DfaInstructionState[] states = super.visitBinop(instruction, runner, memState);
      PsiElement anchor = instruction.getPsiAnchor();
      if (anchor instanceof PsiExpression) {
        for (DfaInstructionState state : states) {
          DfaMemoryState afterState = state.getMemoryState();
          myResult.add((PsiExpression)anchor, (DfaMemoryStateImpl)afterState, afterState.peek());
        }
      }
      return states;
    }

    @NotNull
    @Override
    protected DfaCallArguments popCall(MethodCallInstruction instruction,
                                       DataFlowRunner runner,
                                       DfaMemoryState memState,
                                       boolean contractOnly) {
      DfaCallArguments arguments = super.popCall(instruction, runner, memState, contractOnly);
      PsiElement context = instruction.getContext();
      if (instruction.getMethodType() == MethodCallInstruction.MethodType.REGULAR_METHOD_CALL &&
          context instanceof PsiMethodCallExpression) {
        PsiExpression qualifier =
          PsiUtil.skipParenthesizedExprDown(((PsiMethodCallExpression)context).getMethodExpression().getQualifierExpression());
        if (qualifier != null) {
          myResult.add(qualifier, (DfaMemoryStateImpl)memState, arguments.myQualifier);
        }
      }
      return arguments;
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction,
                                                 DataFlowRunner runner,
                                                 DfaMemoryState memState) {
      DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
      PsiExpression context = ObjectUtils.tryCast(instruction.getContext(), PsiExpression.class);
      if (context != null && ExpressionUtils.getCallForQualifier(context) == null) {
        for (DfaInstructionState state : states) {
          DfaValue value = state.getMemoryState().peek();
          if (value != myFail) {
            myResult.add(context, (DfaMemoryStateImpl)state.getMemoryState(), value);
          }
        }
      }
      return states;
    }
  }
}

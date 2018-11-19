// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.EndOfInitializerInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
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
    private final Object VALUE_NOT_KNOWN = ObjectUtils.sentinel("VALUE_NOT_KNOWN");

    private final Map<PsiExpression, DfaFactMap> myFacts = new HashMap<>();
    private final Map<PsiExpression, Object> myValues = new HashMap<>();
    private final Map<PsiExpression, Set<Object>> myNotValues = new HashMap<>();

    DataflowResult copy() {
      DataflowResult copy = new DataflowResult();
      copy.myFacts.putAll(myFacts);
      return copy;
    }

    void add(PsiExpression expression, DfaMemoryStateImpl memState, DfaValue value) {
      addFacts(expression, memState, value);
      addValue(expression, memState, value);
      addNotValues(expression, memState, value);
    }

    private void addNotValues(PsiExpression expression, DfaMemoryStateImpl memState, DfaValue value) {
      // We do not store not-values for integral numbers as this functionality is covered by range fact
      if (value instanceof DfaVariableValue && !TypeConversionUtil.isIntegralNumberType(value.getType())) {
        Set<Object> notValues = myNotValues.get(expression);
        if (notValues == null) {
          Set<Object> constants = memState.getNonEqualConstants((DfaVariableValue)value);
          myNotValues.put(expression, constants.isEmpty() ? Collections.emptySet() : constants);
        } else if (!notValues.isEmpty()) {
          notValues.retainAll(memState.getNonEqualConstants((DfaVariableValue)value));
          if (notValues.isEmpty()) {
            myNotValues.put(expression, Collections.emptySet());
          }
        }
      }
    }

    private void addValue(PsiExpression expression, DfaMemoryStateImpl memState, DfaValue value) {
      Object curValue = myValues.get(expression);
      if (curValue != VALUE_NOT_KNOWN) {
        DfaConstValue constantValue = memState.getConstantValue(value);
        Object newValue = constantValue == null ? null : constantValue.getValue();
        if (newValue == null || curValue != null && !Objects.equals(curValue, newValue)) {
          newValue = VALUE_NOT_KNOWN;
        }
        myValues.put(expression, newValue);
      }
    }

    private void addFacts(PsiExpression expression, DfaMemoryStateImpl memState, DfaValue value) {
      DfaFactMap existing = myFacts.get(expression);
      if(existing != DfaFactMap.EMPTY) {
        DfaFactMap newMap = memState.getFactMap(value);
        if (!DfaNullability.isNotNull(newMap) && memState.isNotNull(value)) {
          newMap = newMap.with(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL);
        }
        if (value instanceof DfaVariableValue) {
          SpecialField field = SpecialField.fromQualifierType(value.getType());
          if (field != null) {
            DfaConstValue constValue = memState.getConstantValue(field.createValue(value.getFactory(), value));
            if (constValue != null) {
              newMap = newMap.with(DfaFactType.SPECIAL_FIELD_VALUE, field.withValue(constValue.getValue(), constValue.getType()));
            }
          }
        }
        myFacts.put(expression, existing == null ? newMap : existing.unite(newMap));

        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiConditionalExpression &&
            !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), expression, false)) {
          add((PsiExpression)parent, memState, value);
        }
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
      if (expression instanceof PsiParenthesizedExpression) {
        throw new IllegalArgumentException("Should not pass parenthesized expression");
      }
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
     * Returns an expression value if known
     *
     * @param expression an expression to get its value
     * @return a value or null if not known
     */
    @Nullable
    @Contract("null -> null")
    public Object getExpressionValue(@Nullable PsiExpression expression) {
      return this.myValues.get(expression);
    }

    /**
     * Returns a set of values which are known to be not equal to given expression.
     * An empty list is returned if nothing is known.
     *
     * <p>
     * This method may return nothing if {@link #getExpressionValue(PsiExpression)}
     * returns some value (if expression value is known, it's not equal to any other value),
     * or if expression type is an integral type (in this case use
     * {@code getExpressionFact(expression, DfaFactType.RANGE)} which would provide more information anyway).
     *
     * @param expression an expression to get values not equal to.
     * @return a set of values; empty set if nothing is known or this expression was not tracked.
     */
    @NotNull
    public Set<Object> getValuesNotEqualToExpression(@Nullable PsiExpression expression) {
      return myNotValues.getOrDefault(expression, Collections.emptySet());
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
    CommonDataflowVisitor visitor = new CommonDataflowVisitor();
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
    private DataflowResult myResult = new DataflowResult();
    private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();

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
    protected void beforeExpressionPush(@NotNull DfaValue value,
                                     @NotNull PsiExpression expression,
                                     @Nullable TextRange range,
                                     @NotNull DfaMemoryState state) {
      if (range == null && !DfaConstValue.isContractFail(value)) {
        // Do not track instructions which cover part of expression
        myResult.add(expression, (DfaMemoryStateImpl)state, value);
      }
    }
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.EndOfInitializerInstruction;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaFactMapValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

import static com.intellij.codeInspection.dataFlow.DfaUtil.hasImplicitImpureSuperCall;

public class CommonDataflow {
  private static class DataflowPoint {
    // null = top; empty = bottom
    @Nullable DfaFactMap myFacts = null;
    // empty = top; null = bottom
    @Nullable Set<Object> myPossibleValues = Collections.emptySet();
    // null = top; empty = bottom
    @Nullable Set<Object> myNotValues = null;
    boolean myMayFailByContract = false;

    DataflowPoint() {}

    DataflowPoint(DataflowPoint other) {
      myFacts = other.myFacts;
      myPossibleValues = other.myPossibleValues;
      myNotValues = other.myNotValues == null || other.myNotValues.isEmpty() ? other.myNotValues : new HashSet<>(other.myNotValues);
      myMayFailByContract = other.myMayFailByContract;
    }

    void addNotValues(DfaMemoryStateImpl memState, DfaValue value) {
      // We do not store not-values for integral numbers as this functionality is covered by range fact
      if (value instanceof DfaVariableValue && !TypeConversionUtil.isIntegralNumberType(value.getType())) {
        Set<Object> notValues = myNotValues;
        if (notValues == null) {
          Set<Object> constants = memState.getNonEqualConstants((DfaVariableValue)value);
          myNotValues = constants.isEmpty() ? Collections.emptySet() : constants;
        }
        else if (!notValues.isEmpty()) {
          notValues.retainAll(memState.getNonEqualConstants((DfaVariableValue)value));
          if (notValues.isEmpty()) {
            myNotValues = Collections.emptySet();
          }
        }
      }
    }

    void addValue(DfaMemoryStateImpl memState, DfaValue value) {
      if (myPossibleValues == null) return;
      DfaConstValue constantValue = memState.getConstantValue(value);
      if (constantValue == null) {
        myPossibleValues = null;
        return;
      }
      Object newValue = constantValue.getValue();
      if (myPossibleValues.contains(newValue)) return;
      myNotValues = null;
      if (myPossibleValues.isEmpty()) {
        myPossibleValues = Collections.singleton(newValue);
      }
      else {
        myPossibleValues = new HashSet<>(myPossibleValues);
        myPossibleValues.add(newValue);
      }
    }

    void addFacts(DfaMemoryStateImpl memState, DfaValue value) {
      if (myFacts == DfaFactMap.EMPTY) return;
      DfaFactMap newMap = DataflowResult.getFactMap(memState, value);
      if (value instanceof DfaVariableValue) {
        SpecialField field = SpecialField.fromQualifier(value);
        if (field != null) {
          DfaValue specialField = field.createValue(value.getFactory(), value);
          if (specialField instanceof DfaVariableValue) {
            DfaConstValue constantValue = memState.getConstantValue(specialField);
            specialField = constantValue != null
                           ? constantValue
                           : specialField.getFactory().getFactFactory().createValue(DataflowResult.getFactMap(memState, specialField));
          }
          if (specialField instanceof DfaConstValue || specialField instanceof DfaFactMapValue) {
            newMap = newMap.with(DfaFactType.SPECIAL_FIELD_VALUE, field.withValue(specialField));
          }
        }
      }
      myFacts = myFacts == null ? newMap : myFacts.unite(newMap);
    }
  }
  
  /**
   * Represents the result of dataflow applied to some code fragment (usually a method)
   */
  public static final class DataflowResult {
    private final Map<PsiExpression, DataflowPoint> myData = new HashMap<>();
    private final RunnerResult myResult;

    public DataflowResult(RunnerResult result) {
      myResult = result;
    }

    @NotNull
    DataflowResult copy() {
      DataflowResult copy = new DataflowResult(myResult);
      myData.forEach((expression, point) -> copy.myData.put(expression, new DataflowPoint(point)));
      return copy;
    }

    void add(PsiExpression expression, DfaMemoryStateImpl memState, DfaValue value) {
      DataflowPoint point = myData.computeIfAbsent(expression, e -> new DataflowPoint());
      if (DfaConstValue.isContractFail(value)) {
        point.myMayFailByContract = true;
        return;
      }
      if (point.myFacts != DfaFactMap.EMPTY) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiConditionalExpression &&
            !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), expression, false)) {
          add((PsiExpression)parent, memState, value);
        }
      }
      point.addFacts(memState, value);
      point.addValue(memState, value);
      point.addNotValues(memState, value);
    }

    @NotNull
    private static DfaFactMap getFactMap(DfaMemoryStateImpl memState, DfaValue value) {
      DfaFactMap newMap = memState.getFactMap(value);
      DfaNullability nullability = newMap.get(DfaFactType.NULLABILITY);
      if (nullability != DfaNullability.NOT_NULL && memState.isNotNull(value)) {
        newMap = newMap.with(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL);
      }
      return newMap;
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
      return myData.containsKey(expression);
    }

    /**
     * Returns true if given call cannot fail according to its contracts 
     * (e.g. {@code Optional.get()} executed under {@code Optional.isPresent()}).
     * 
     * @param call call to check
     * @return true if it cannot fail by contract; false if unknown or can fail
     */
    public boolean cannotFailByContract(PsiCallExpression call) {
      DataflowPoint point = myData.get(call);
      return point != null && !point.myMayFailByContract;
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
      DataflowPoint point = myData.get(expression);
      return point == null || point.myFacts == null ? null : point.myFacts.get(type);
    }

    /**
     * Returns a set of expression values if known. If non-empty set is returned, then given expression
     * is guaranteed to have one of returned values.
     *
     * @param expression an expression to get its value
     * @return a set of possible values or empty set if not known
     */
    @NotNull
    public Set<Object> getExpressionValues(@Nullable PsiExpression expression) {
      DataflowPoint point = myData.get(expression);
      if (point == null) return Collections.emptySet();
      Set<Object> values = point.myPossibleValues;
      return values == null ? Collections.emptySet() : Collections.unmodifiableSet(values);
    }

    /**
     * Returns a set of values which are known to be not equal to given expression.
     * An empty list is returned if nothing is known.
     *
     * <p>
     * This method may return nothing if {@link #getExpressionValues(PsiExpression)}
     * returns some values (if expression values are known, it's not equal to any other value),
     * or if expression type is an integral type (in this case use
     * {@code getExpressionFact(expression, DfaFactType.RANGE)} which would provide more information anyway).
     *
     * @param expression an expression to get values not equal to.
     * @return a set of values; empty set if nothing is known or this expression was not tracked.
     */
    @NotNull
    public Set<Object> getValuesNotEqualToExpression(@Nullable PsiExpression expression) {
      DataflowPoint point = myData.get(expression);
      if (point == null) return Collections.emptySet();
      Set<Object> values = point.myNotValues;
      return values == null ? Collections.emptySet() : Collections.unmodifiableSet(values);
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
      DataflowPoint point = myData.get(expression);
      return point == null ? null : point.myFacts;
    }
  }

  @NotNull
  private static DataflowResult runDFA(@Nullable PsiElement block) {
    if (block == null) return new DataflowResult(RunnerResult.NOT_APPLICABLE);
    DataFlowRunner runner = new DataFlowRunner(false, block);
    CommonDataflowVisitor visitor = new CommonDataflowVisitor();
    RunnerResult result = runner.analyzeMethodRecursively(block, visitor, false);
    if (result != RunnerResult.OK) return new DataflowResult(result);
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
      if(runner.analyzeBlockRecursively(body, initialStates, visitor, false) == RunnerResult.OK) {
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
    ConcurrentHashMap<PsiElement, DataflowResult> fileMap =
      CachedValuesManager.getCachedValue(body.getContainingFile(), () ->
        CachedValueProvider.Result.create(new ConcurrentHashMap<>(), PsiModificationTracker.MODIFICATION_COUNT));
    class ManagedCompute implements ForkJoinPool.ManagedBlocker {
      DataflowResult myResult;

      @Override
      public boolean block() {
        myResult = fileMap.computeIfAbsent(body, CommonDataflow::runDFA);
        return true;
      }

      @Override
      public boolean isReleasable() {
        myResult = fileMap.get(body);
        return myResult != null;
      }

      DataflowResult getResult() {
        return myResult == null || myResult.myResult != RunnerResult.OK ? null : myResult;
      }
    }
    ManagedCompute managedCompute = new ManagedCompute();
    try {
      ForkJoinPool.managedBlock(managedCompute);
    }
    catch (RejectedExecutionException ex) {
      // Too many FJP threads: execute anyway in current thread
      managedCompute.block();
    }
    catch (InterruptedException ex) {
      // Should not happen
      throw new AssertionError(ex);
    }
    return managedCompute.getResult();
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

  /**
   * Returns long range set for expression or null if range is unknown.
   * This method first tries to compute expression using {@link com.intellij.psi.impl.ConstantExpressionEvaluator}
   * and only then calls {@link #getExpressionFact(PsiExpression, DfaFactType)}.
   *
   * @param expression expression to get its range
   * @return long range set
   */
  @Contract("null -> null")
  @Nullable
  public static LongRangeSet getExpressionRange(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    Object value = ExpressionUtils.computeConstantExpression(expression);
    LongRangeSet rangeSet = LongRangeSet.fromConstant(value);
    if (rangeSet != null) return rangeSet;
    return getExpressionFact(expression, DfaFactType.RANGE);
  }

  private static class CommonDataflowVisitor extends StandardInstructionVisitor {
    private DataflowResult myResult = new DataflowResult(RunnerResult.OK);
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
      if (range == null) {
        // Do not track instructions which cover part of expression
        myResult.add(expression, (DfaMemoryStateImpl)state, value);
      }
    }
  }
}

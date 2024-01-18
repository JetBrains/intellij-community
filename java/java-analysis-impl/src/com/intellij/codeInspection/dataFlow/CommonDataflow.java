// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.interpreter.ReachabilityCountingInterpreter;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.java.anchor.*;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

import static com.intellij.codeInspection.dataFlow.DfaUtil.hasImplicitImpureSuperCall;

public final class CommonDataflow {
  private CommonDataflow() {}
  
  private static class DataflowPoint {
    @NotNull DfType myDfType = DfType.BOTTOM;
    // empty = top; null = bottom
    @Nullable Set<Object> myPossibleValues = Collections.emptySet();
    boolean myMayFailByContract = false;

    DataflowPoint() {}

    DataflowPoint(DataflowPoint other) {
      myDfType = other.myDfType;
      myPossibleValues = other.myPossibleValues;
      myMayFailByContract = other.myMayFailByContract;
    }

    void addValue(DfaMemoryState memState, DfaValue value) {
      if (myPossibleValues == null) return;
      DfType dfType = memState.getDfType(value);
      Object newValue = dfType.getConstantOfType(Object.class);
      if (newValue == null && !dfType.equals(DfTypes.NULL)) {
        myPossibleValues = null;
        return;
      }
      if (myPossibleValues.contains(newValue)) return;
      if (myPossibleValues.isEmpty()) {
        myPossibleValues = Collections.singleton(newValue);
      }
      else {
        myPossibleValues = new HashSet<>(myPossibleValues);
        myPossibleValues.add(newValue);
      }
    }

    void addFacts(DfaMemoryState memState, DfaValue value) {
      if (myDfType == DfType.TOP) return;
      DfType newType = memState.getDfTypeIncludingDerived(value);
      myDfType = myDfType.join(newType);
    }
  }

  /**
   * Represents the result of dataflow applied to some code fragment (usually a method)
   */
  public static final class DataflowResult {
    private final @NotNull Map<JavaDfaAnchor, DataflowPoint> myData = new HashMap<>();
    private final @NotNull List<TextRange> myUnreachable = new ArrayList<>();
    private @NotNull Map<JavaDfaAnchor, DataflowPoint> myDataAssertionsDisabled = myData;
    private final @NotNull RunnerResult myResult;

    public DataflowResult(@NotNull RunnerResult result) {
      myResult = result;
    }

    @NotNull
    DataflowResult copy() {
      DataflowResult copy = new DataflowResult(myResult);
      myData.forEach((anchor, point) -> copy.myData.put(anchor, new DataflowPoint(point)));
      copy.myUnreachable.addAll(myUnreachable);
      return copy;
    }

    void add(JavaDfaAnchor anchor, DfaMemoryState memState, DfaValue value) {
      DfaVariableValue assertionDisabled = AssertionDisabledDescriptor.getAssertionsDisabledVar(value.getFactory());
      if (assertionDisabled == null) {
        assert myData == myDataAssertionsDisabled;
        updateDataPoint(myData, anchor, memState, value);
      } else {
        DfType type = memState.getDfType(assertionDisabled);
        if (type == DfTypes.TRUE || type == DfTypes.FALSE) {
          if (myData == myDataAssertionsDisabled) {
            myDataAssertionsDisabled = new HashMap<>(myData);
          }
          updateDataPoint(type == DfTypes.TRUE ? myDataAssertionsDisabled : myData, anchor, memState, value);
        } else {
          updateDataPoint(myData, anchor, memState, value);
          if (myData != myDataAssertionsDisabled) {
            updateDataPoint(myDataAssertionsDisabled, anchor, memState, value);
          }
        }
      }
    }

    private void updateDataPoint(Map<JavaDfaAnchor, DataflowPoint> data,
                                 JavaDfaAnchor anchor,
                                 DfaMemoryState memState,
                                 DfaValue value) {
      DataflowPoint point = data.computeIfAbsent(anchor, e -> new DataflowPoint());
      if (DfaTypeValue.isContractFail(value)) {
        point.myMayFailByContract = true;
        return;
      }
      if (point.myDfType != DfType.TOP && anchor instanceof JavaExpressionAnchor) {
        PsiExpression expression = ((JavaExpressionAnchor)anchor).getExpression();
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiConditionalExpression &&
            !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), expression, false)) {
          add(new JavaExpressionAnchor((PsiExpression)parent), memState, value);
        }
      }
      point.addFacts(memState, value);
      point.addValue(memState, value);
    }

    /**
     * @param anchor anchor to check
     * @return true if a given anchor appeared during the analysis
     */
    public boolean anchorWasAnalyzed(@NotNull JavaDfaAnchor anchor) {
      return myData.containsKey(anchor);
    }

    /**
     * Returns true if given call cannot fail according to its contracts
     * (e.g. {@code Optional.get()} executed under {@code Optional.isPresent()}).
     *
     * @param call call to check
     * @return true if it cannot fail by contract; false if unknown or can fail
     */
    @Contract("null -> false")
    public boolean cannotFailByContract(@Nullable PsiCallExpression call) {
      if (call == null) return false;
      DataflowPoint point = myData.get(new JavaExpressionAnchor(call));
      return point != null && !point.myMayFailByContract;
    }
    
    public @NotNull Collection<TextRange> getUnreachableRanges() {
      return myResult != RunnerResult.OK ? Collections.emptyList() : myUnreachable;
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
      if (expression == null) return Collections.emptySet();
      DataflowPoint point = myData.get(new JavaExpressionAnchor(expression));
      if (point == null) return Collections.emptySet();
      Set<Object> values = point.myPossibleValues;
      return values == null ? Collections.emptySet() : Collections.unmodifiableSet(values);
    }

    /**
     * @param expression an expression to infer the DfType, must be deparenthesized.
     * @return DfType for that expression, assuming assertions are disabled.
     * May return {@link DfType#TOP} if no information from dataflow is known about this expression
     * @see #getDfTypeNoAssertions(PsiExpression)
     */
    @NotNull
    public DfType getDfType(PsiExpression expression) {
      if (expression == null) return DfType.TOP;
      DataflowPoint point = myData.get(new JavaExpressionAnchor(expression));
      return point == null ? DfType.TOP : point.myDfType;
    }

    @NotNull
    public DfType getDfType(@NotNull JavaDfaAnchor anchor) {
      DataflowPoint point = myData.get(anchor);
      return point == null ? DfType.TOP : point.myDfType;
    }

    @NotNull
    public DfType getDfTypeNoAssertions(@NotNull JavaDfaAnchor anchor) {
      DataflowPoint point = myDataAssertionsDisabled.get(anchor);
      return point == null ? DfType.TOP : point.myDfType;
    }

    /**
     * @param expression an expression to infer the DfType, must be deparenthesized.
     * @return DfType for that expression, assuming assertions are disabled.
     * May return {@link DfType#TOP} if no information from dataflow is known about this expression
     * @see #getDfType(PsiExpression)
     */
    @NotNull
    public DfType getDfTypeNoAssertions(PsiExpression expression) {
      if (expression == null) return DfType.TOP;
      DataflowPoint point = myDataAssertionsDisabled.get(new JavaExpressionAnchor(expression));
      return point == null ? DfType.TOP : point.myDfType;
    }
  }

  @NotNull
  private static DataflowResult runDFA(@Nullable PsiElement block) {
    if (block == null) return new DataflowResult(RunnerResult.NOT_APPLICABLE);
    var listener = new CommonDataflowListener();
    var runner = new StandardDataFlowRunner(block.getProject(), ThreeState.UNSURE) {
      @Override
      protected @NotNull StandardDataFlowInterpreter createInterpreter(@NotNull DfaListener listener, @NotNull ControlFlow flow) {
        return new ReachabilityCountingInterpreter(flow, listener, false, 0);
      }

      @Override
      protected void afterInterpretation(@NotNull ControlFlow flow,
                                         @NotNull StandardDataFlowInterpreter interpreter,
                                         @NotNull RunnerResult result) {
        if (result == RunnerResult.OK) {
          Set<PsiElement> unreachable = ((ReachabilityCountingInterpreter)interpreter).getUnreachable();
          listener.myResult.myUnreachable.addAll(DataFlowIRProvider.computeUnreachableSegments(block, unreachable));
        }
        super.afterInterpretation(flow, interpreter, result);
      }
    };
    RunnerResult result = runner.analyzeMethodRecursively(block, listener);
    if (result != RunnerResult.OK) return new DataflowResult(result);
    if (!(block instanceof PsiClass psiClass)) return listener.myResult;
    DataflowResult dfr = listener.myResult.copy();
    List<DfaMemoryState> states = listener.myEndOfInitializerStates;
    for (PsiMethod method : psiClass.getConstructors()) {
      List<DfaMemoryState> initialStates;
      PsiCodeBlock body = method.getBody();
      if (body == null) continue;
      PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
      if (JavaPsiConstructorUtil.isChainedConstructorCall(call) || (call == null && hasImplicitImpureSuperCall(psiClass, method))) {
        initialStates = Collections.singletonList(runner.createMemoryState());
      } else {
        initialStates = ContainerUtil.map(states, DfaMemoryState::createCopy);
      }
      if (runner.analyzeBlockRecursively(body, initialStates, listener) == RunnerResult.OK) {
        dfr = listener.myResult.copy();
      } else {
        listener.myResult = dfr;
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
  public static DataflowResult getDataflowResult(@NotNull PsiElement context) {
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
   * @param expression an expression to infer the DfType
   * @return DfType for that expression. May return {@link DfType#TOP} if no information from dataflow is known about this expression
   */
  @NotNull
  public static DfType getDfType(PsiExpression expression) {
    return getDfType(expression, false);
  }

  /**
   * @param expression an expression to infer the DfType
   * @param ignoreAssertions whether to ignore assertion statement during the analysis
   * @return DfType for that expression. May return {@link DfType#TOP} if no information from dataflow is known about this expression
   */
  @NotNull
  public static DfType getDfType(PsiExpression expression, boolean ignoreAssertions) {
    DataflowResult result = getDataflowResult(expression);
    if (result == null) return DfType.TOP;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return ignoreAssertions ? result.getDfTypeNoAssertions(expression) : result.getDfType(expression);
  }

  /**
   * Returns long range set for expression or null if range is unknown.
   * This method first tries to compute expression using {@link com.intellij.psi.impl.ConstantExpressionEvaluator}
   * and only then calls {@link #getDfType(PsiExpression)}.
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
    DfType dfType = getDfType(expression);
    return dfType instanceof DfIntegralType ? ((DfIntegralType)dfType).getRange() : null;
  }

  /**
   * Returns the value of given expression calculated via dataflow; or null if value is null or unknown.
   *
   * @param expression expression to analyze
   * @return expression value if known
   */
  @Contract("null -> null")
  public static Object computeValue(@Nullable PsiExpression expression) {
    PsiExpression expressionToAnalyze = PsiUtil.skipParenthesizedExprDown(expression);
    if (expressionToAnalyze == null) return null;
    Object computed = ExpressionUtils.computeConstantExpression(expressionToAnalyze);
    if (computed != null) return computed;
    return getDfType(expressionToAnalyze).getConstantOfType(Object.class);
  }

  private static class CommonDataflowListener implements JavaDfaListener {
    private DataflowResult myResult = new DataflowResult(RunnerResult.OK);
    private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();

    @Override
    public void beforeInstanceInitializerEnd(@NotNull DfaMemoryState state) {
      myEndOfInitializerStates.add(state.createCopy());
    }

    @Override
    public void beforeExpressionPush(@NotNull DfaValue value,
                                     @NotNull PsiExpression expression,
                                     @NotNull DfaMemoryState state) {
      myResult.add(new JavaExpressionAnchor(expression), state, value);
    }

    @Override
    public void beforePush(@NotNull DfaValue @NotNull [] args,
                           @NotNull DfaValue value,
                           @NotNull DfaAnchor anchor,
                           @NotNull DfaMemoryState state) {
      JavaDfaListener.super.beforePush(args, value, anchor, state);
      if (anchor instanceof JavaMethodReferenceArgumentAnchor || anchor instanceof JavaPolyadicPartAnchor ||
          anchor instanceof JavaMethodReferenceReturnAnchor) {
        myResult.add((JavaDfaAnchor)anchor, state, value);
      }
    }

    @Override
    public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                            @NotNull DfaValue value,
                            @NotNull ThreeState failed,
                            @NotNull DfaMemoryState state) {
      if (problem instanceof ContractFailureProblem && failed != ThreeState.NO) {
        myResult.add(new JavaExpressionAnchor(((ContractFailureProblem)problem).getAnchor()), state,
                     value.getFactory().fromDfType(DfType.FAIL));
      }
    }
  }
}

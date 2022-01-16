// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase.ConstantResult;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaSwitchLabelTakenAnchor;
import com.intellij.codeInspection.dataFlow.java.inst.InstanceofInstruction;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.*;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

final class DataFlowInstructionVisitor implements JavaDfaListener {
  private static final Logger LOG = Logger.getInstance(DataFlowInstructionVisitor.class);
  private final Map<NullabilityProblemKind.NullabilityProblem<?>, StateInfo> myStateInfos = new LinkedHashMap<>();
  private final Map<PsiTypeCastExpression, StateInfo> myClassCastProblems = new HashMap<>();
  private final Map<PsiTypeCastExpression, TypeConstraint> myRealOperandTypes = new HashMap<>();
  private final Map<PsiCallExpression, Boolean> myFailingCalls = new HashMap<>();
  private final Map<DfaAnchor, ConstantResult> myConstantExpressions = new HashMap<>();
  private final Map<PsiElement, ThreeState> myOfNullableCalls = new HashMap<>();
  private final Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> myArrayStoreProblems = new HashMap<>();
  private final Map<PsiArrayAccessExpression, ThreeState> myOutOfBoundsArrayAccesses = new HashMap<>();
  private final Map<PsiExpression, ThreeState> myNegativeArraySizes = new HashMap<>();
  private final Set<PsiElement> myReceiverMutabilityViolation = new HashSet<>();
  private final Set<PsiElement> myArgumentMutabilityViolation = new HashSet<>();
  private final Map<PsiExpression, Boolean> mySameValueAssigned = new HashMap<>();
  private final Map<PsiReferenceExpression, ArgResultEquality> mySameArguments = new HashMap<>();
  private final Map<PsiCaseLabelElement, ThreeState> mySwitchLabelsReachability = new HashMap<>();
  private boolean myAlwaysReturnsNotNull = true;
  private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();
  private final Set<DfaAnchor> myPotentiallyRedundantInstanceOf = new HashSet<>();
  private final boolean myStrictMode;

  private static final CallMatcher USELESS_SAME_ARGUMENTS = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_MATH, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "min", "max").parameterCount(2),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "replace").parameterCount(2)
  );

  DataFlowInstructionVisitor(boolean strictMode) {
    myStrictMode = strictMode;
  }

  @Override
  public void beforeAssignment(@NotNull DfaValue value,
                               @NotNull DfaValue target,
                               @NotNull DfaMemoryState memState,
                               @Nullable DfaAnchor anchor) {
    if (!(anchor instanceof JavaExpressionAnchor)) return;
    PsiAssignmentExpression assignment = tryCast(((JavaExpressionAnchor)anchor).getExpression(), PsiAssignmentExpression.class);
    if (assignment == null) return;
    PsiExpression left = assignment.getLExpression();
    if (!Boolean.FALSE.equals(mySameValueAssigned.get(left))) {
      if (!left.isPhysical()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Non-physical element in assignment instruction: " + left.getParent().getText(), new Throwable());
        }
      } else {
        DfType dfType = memState.getDfType(value);
        // Reporting strings is skipped because string reassignment might be intentionally used to deduplicate the heap objects
        // (we compare strings by contents)
        if (memState.areEqual(value, target) &&
            !isFloatingZero(dfType.getConstantOfType(Number.class)) &&
            !(TypeUtils.isJavaLangString(left.getType()) && dfType != DfTypes.NULL) &&
            !isAssignmentToDefaultValueInConstructor(target, assignment.getRExpression())) {
          mySameValueAssigned.merge(left, Boolean.TRUE, Boolean::logicalAnd);
        }
        else {
          mySameValueAssigned.put(left, Boolean.FALSE);
        }
      }
    }
  }

  void initInstanceOf(Instruction[] instructions) {
    StreamEx.of(instructions).select(InstanceofInstruction.class).map(InstanceofInstruction::getDfaAnchor).into(
      myPotentiallyRedundantInstanceOf);
  }

  private static boolean isAssignmentToDefaultValueInConstructor(DfaValue target, PsiExpression rExpression) {
    if (!(target instanceof DfaVariableValue)) return false;
    DfaVariableValue var = (DfaVariableValue)target;
    PsiField field = tryCast(var.getPsiVariable(), PsiField.class);
    if (field == null || var.getQualifier() == null || !(var.getQualifier().getDescriptor() instanceof ThisDescriptor)) {
      return false;
    }

    // chained assignment like this.a = this.b = 0; is also supported
    while (rExpression instanceof PsiAssignmentExpression &&
           ((PsiAssignmentExpression)rExpression).getOperationTokenType().equals(JavaTokenType.EQ)) {
      rExpression = ((PsiAssignmentExpression)rExpression).getRExpression();
    }
    DfaValue dest = JavaDfaValueFactory.getExpressionDfaValue(var.getFactory(), rExpression);
    if (dest == null) return false;
    DfType dfType = dest.getDfType();

    PsiType type = field.getType();
    boolean isDefaultValue = dfType.isConst(PsiTypesUtil.getDefaultValue(type)) ||
                             dfType.isConst(0) && TypeConversionUtil.isIntegralNumberType(type);
    if (!isDefaultValue) return false;
    PsiMethod method = PsiTreeUtil.getParentOfType(rExpression, PsiMethod.class);
    return method != null && method.isConstructor();
  }

  // Reporting of floating zero is skipped, because this produces false-positives on the code like
  // if(x == -0.0) x = 0.0;
  private static boolean isFloatingZero(Object value) {
    if (value instanceof Double) {
      return ((Double)value).doubleValue() == 0.0;
    }
    if (value instanceof Float) {
      return ((Float)value).floatValue() == 0.0f;
    }
    return false;
  }

  StreamEx<PsiExpression> sameValueAssignments() {
    return StreamEx.ofKeys(mySameValueAssigned, Boolean::booleanValue);
  }

  EntryStream<PsiReferenceExpression, ArgResultEquality> pointlessSameArguments() {
    return EntryStream.of(mySameArguments).filterValues(ArgResultEquality::hasEquality);
  }

  StreamEx<NullabilityProblemKind.NullabilityProblem<?>> problems() {
    return EntryStream.of(myStateInfos).filterValues(StateInfo::shouldReport).mapKeyValue((np, si) -> si.unknown ? np.makeUnknown() : np);
  }

  public Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> getArrayStoreProblems() {
    return myArrayStoreProblems;
  }

  Map<PsiElement, ThreeState> getOfNullableCalls() {
    return myOfNullableCalls;
  }

  Map<PsiExpression, ConstantResult> getConstantExpressions() {
    return EntryStream.of(myConstantExpressions).selectKeys(JavaExpressionAnchor.class)
      .mapKeys(JavaExpressionAnchor::getExpression).toMap();
  }

  Map<DfaAnchor, ConstantResult> getConstantExpressionChunks() {
    return myConstantExpressions;
  }

  Map<PsiCaseLabelElement, ThreeState> getSwitchLabelsReachability() {
    return mySwitchLabelsReachability;
  }

  EntryStream<PsiTypeCastExpression, Pair<Boolean, PsiType>> getFailingCastExpressions() {
    return EntryStream.of(myClassCastProblems).filterValues(StateInfo::shouldReport).mapToValue(
      (cast, info) -> Pair.create(info.alwaysFails(), myRealOperandTypes.getOrDefault(cast, TypeConstraints.TOP).getPsiType(cast.getProject())));
  }

  Set<PsiElement> getMutabilityViolations(boolean receiver) {
    return receiver ? myReceiverMutabilityViolation : myArgumentMutabilityViolation;
  }

  public List<DfaMemoryState> getEndOfInitializerStates() {
    return myEndOfInitializerStates;
  }

  Stream<PsiArrayAccessExpression> outOfBoundsArrayAccesses() {
    return StreamEx.ofKeys(myOutOfBoundsArrayAccesses, ThreeState.YES::equals);
  }

  Stream<PsiExpression> negativeArraySizes() {
    return StreamEx.ofKeys(myNegativeArraySizes, ThreeState.YES::equals);
  }

  StreamEx<PsiCallExpression> alwaysFailingCalls() {
    return StreamEx.ofKeys(myFailingCalls, v -> v);
  }

  boolean isAlwaysReturnsNotNull(Instruction[] instructions) {
    return myAlwaysReturnsNotNull &&
           ContainerUtil.exists(instructions, i -> i instanceof ReturnInstruction && ((ReturnInstruction)i).getAnchor() instanceof PsiReturnStatement);
  }
  
  StreamEx<DfaAnchor> redundantInstanceOfs() {
    return StreamEx.of(myPotentiallyRedundantInstanceOf).filter(anchor -> myConstantExpressions.get(anchor) == ConstantResult.UNKNOWN);
  }

  @Override
  public void beforePush(@NotNull DfaValue @NotNull [] args,
                         @NotNull DfaValue value,
                         @NotNull DfaAnchor anchor,
                         @NotNull DfaMemoryState state) {
    JavaDfaListener.super.beforePush(args, value, anchor, state);
    if (anchor instanceof JavaExpressionAnchor) {
      PsiExpression expression = ((JavaExpressionAnchor)anchor).getExpression();
      if (expression instanceof PsiLiteralExpression) return;
      if (expression instanceof PsiMethodCallExpression && USELESS_SAME_ARGUMENTS.matches(expression)) {
        checkUselessCall(args, value, state, ((PsiMethodCallExpression)expression).getMethodExpression());
      }
    }
    if (anchor instanceof JavaMethodReferenceReturnAnchor) {
      PsiMethodReferenceExpression methodRef = ((JavaMethodReferenceReturnAnchor)anchor).getMethodReferenceExpression();
      if (USELESS_SAME_ARGUMENTS.methodReferenceMatches(methodRef)) {
        checkUselessCall(args, value, state, methodRef);
      }
    }
    if (anchor instanceof JavaSwitchLabelTakenAnchor) {
      DfType type = state.getDfType(value);
      mySwitchLabelsReachability.merge(((JavaSwitchLabelTakenAnchor)anchor).getLabelElement(),
                                       type.equals(DfTypes.TRUE) ? ThreeState.YES :
                                       type.equals(DfTypes.FALSE) ? ThreeState.NO : ThreeState.UNSURE, ThreeState::merge);
      return;
    }
    if (myPotentiallyRedundantInstanceOf.contains(anchor) && isUsefulInstanceof(args, value, state)) {
      myPotentiallyRedundantInstanceOf.remove(anchor);
    }
    myConstantExpressions.compute(anchor, (c, curState) -> ConstantResult.mergeValue(curState, state, value));
  }

  private static boolean isUsefulInstanceof(@NotNull DfaValue @NotNull [] args,
                                            @NotNull DfaValue value,
                                            @NotNull DfaMemoryState state) {
    if (args.length != 2) return true;
    DfType type = state.getDfType(value);
    if (type.equals(DfTypes.BOOLEAN)) {
      if (args[0] instanceof DfaTypeValue && args[1] instanceof DfaTypeValue && !DfaTypeValue.isUnknown(args[0])) {
        TypeConstraint left = TypeConstraint.fromDfType(args[0].getDfType());
        TypeConstraint right = TypeConstraint.fromDfType(args[1].getDfType());
        return !right.isSuperConstraintOf(left);
      }
      return true;
    }
    return type.equals(DfTypes.FALSE) && DfaNullability.fromDfType(state.getDfType(args[0])) != DfaNullability.NULL;
  }

  private void checkUselessCall(@NotNull DfaValue @NotNull [] args,
                         @NotNull DfaValue value,
                         @NotNull DfaMemoryState state,
                         @NotNull PsiReferenceExpression expression) {
    if (args.length == 3) {
      ArgResultEquality equality = new ArgResultEquality(
        state.areEqual(args[1], args[2]),
        state.areEqual(value, args[1]),
        state.areEqual(value, args[2]));
      mySameArguments.merge(expression, equality, ArgResultEquality::merge);
    }
  }

  @Override
  public void beforeExpressionPush(@NotNull DfaValue value,
                                   @NotNull PsiExpression expression,
                                   @NotNull DfaMemoryState memState) {
    if (!expression.isPhysical()) {
      Application application = ApplicationManager.getApplication();
      if (application.isEAP() || application.isInternal() || application.isUnitTestMode()) {
        throw new IllegalStateException("Non-physical expression is passed");
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (OptionalUtil.OPTIONAL_OF_NULLABLE.test(call)) {
        processOfNullableResult(value, memState, call.getArgumentList().getExpressions()[0]);
      }
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiTypeCastExpression) {
      TypeConstraint fact = TypeConstraint.fromDfType(memState.getDfType(value));
      myRealOperandTypes.merge((PsiTypeCastExpression)parent, fact, TypeConstraint::join);
    }
  }

  @Override
  public void beforeValueReturn(@NotNull DfaValue value,
                                @Nullable PsiExpression expression,
                                @NotNull PsiElement context,
                                @NotNull DfaMemoryState state) {
    if (context instanceof PsiMethod || context instanceof PsiLambdaExpression) {
      myAlwaysReturnsNotNull = myAlwaysReturnsNotNull && !state.getDfType(value).isSuperType(DfTypes.NULL);
    }
    else if (context instanceof PsiMethodReferenceExpression) {
      var methodRef = (PsiMethodReferenceExpression)context;
      if (OptionalUtil.OPTIONAL_OF_NULLABLE.methodReferenceMatches(methodRef)) {
        processOfNullableResult(value, state, methodRef.getReferenceNameElement());
      }
    }
  }

  private void processOfNullableResult(@NotNull DfaValue value, @NotNull DfaMemoryState memState, PsiElement anchor) {
    DfaValueFactory factory = value.getFactory();
    DfaValue optionalValue = SpecialField.OPTIONAL_VALUE.createValue(factory, value);
    DfaNullability nullability = DfaNullability.fromDfType(memState.getDfType(optionalValue));
    ThreeState present;
    if (nullability == DfaNullability.NULL) {
      present = ThreeState.NO;
    }
    else if (nullability == DfaNullability.NOT_NULL) {
      present = ThreeState.YES;
    }
    else present = ThreeState.UNSURE;
    myOfNullableCalls.merge(anchor, present, ThreeState::merge);
  }

  @Override
  public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                          @NotNull DfaValue value,
                          @NotNull ThreeState failed,
                          @NotNull DfaMemoryState state) {
    if (problem instanceof MutabilityProblem && failed == ThreeState.YES) {
      reportMutabilityViolation(((MutabilityProblem)problem).isReceiver(), ((MutabilityProblem)problem).getAnchor());
    }
    else if (problem instanceof NegativeArraySizeProblem) {
      myNegativeArraySizes.merge(((NegativeArraySizeProblem)problem).getAnchor(), failed, ThreeState::merge);
    }
    else if (problem instanceof ArrayIndexProblem) {
      myOutOfBoundsArrayAccesses.merge(((ArrayIndexProblem)problem).getAnchor(), failed, ThreeState::merge);
    }
    else if (problem instanceof ClassCastProblem) {
      myClassCastProblems.computeIfAbsent(((ClassCastProblem)problem).getAnchor(), e -> new StateInfo())
        .update(state, ThreeState.fromBoolean(failed != ThreeState.YES));
    }
    else if (problem instanceof ArrayStoreProblem && failed == ThreeState.YES) {
      myArrayStoreProblems.put(((ArrayStoreProblem)problem).getAnchor(),
                               Pair.create(((ArrayStoreProblem)problem).getFromType(), ((ArrayStoreProblem)problem).getToType()));
    }
    else if (problem instanceof ContractFailureProblem) {
      PsiCallExpression call = tryCast(((ContractFailureProblem)problem).getAnchor(), PsiCallExpression.class);
      if (call != null) {
        Boolean isFailing = myFailingCalls.get(call);
        if (isFailing != null || !hasTrivialFailContract(call)) {
          myFailingCalls.put(call, failed == ThreeState.YES && !Boolean.FALSE.equals(isFailing));
        }
      }
    }
    else if (problem instanceof NullabilityProblemKind.NullabilityProblem) {
      DfaNullability nullability = DfaNullability.fromDfType(state.getDfType(value));
      boolean notNullable = nullability != DfaNullability.NULL && nullability != DfaNullability.NULLABLE;
      boolean unknown = myStrictMode && nullability == DfaNullability.UNKNOWN;
      ThreeState ok = notNullable ? unknown ? ThreeState.UNSURE : ThreeState.YES : ThreeState.NO;
      StateInfo info = myStateInfos.computeIfAbsent((NullabilityProblemKind.NullabilityProblem<?>)problem, k -> new StateInfo());
      info.update(state, ok);
    }
  }

  @Override
  public void beforeInstanceInitializerEnd(@NotNull DfaMemoryState state) {
    myEndOfInitializerStates.add(state.createCopy());
  }

  private static boolean hasTrivialFailContract(PsiCallExpression call) {
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
    return contracts.size() == 1 && contracts.get(0).isTrivial() && contracts.get(0).getReturnValue().isFail();
  }

  private void reportMutabilityViolation(boolean receiver, @NotNull PsiElement anchor) {
    if (receiver) {
      if (anchor instanceof PsiMethodReferenceExpression) {
        anchor = ((PsiMethodReferenceExpression)anchor).getReferenceNameElement();
      } else if (anchor instanceof PsiMethodCallExpression) {
        anchor = ((PsiMethodCallExpression)anchor).getMethodExpression().getReferenceNameElement();
      }
      if (anchor != null) {
        myReceiverMutabilityViolation.add(anchor);
      }
    }
    else {
      myArgumentMutabilityViolation.add(anchor);
    }
  }

  private static class StateInfo {
    boolean ephemeralException;
    boolean normalException;
    boolean normalOk;
    boolean unknown = true;

    void update(DfaMemoryState state, ThreeState ok) {
      if (state.isEphemeral()) {
        if (ok != ThreeState.YES) ephemeralException = true;
        if (ok != ThreeState.UNSURE) unknown = false;
      }
      else {
        if (ok == ThreeState.YES) normalOk = true;
        else {
          normalException = true;
          if (ok != ThreeState.UNSURE) unknown = false;
        }
      }
    }

    boolean shouldReport() {
      // non-ephemeral exceptions should be reported
      // ephemeral exceptions should also be reported if only ephemeral states have reached a particular problematic instruction
      //  (e.g. if it's inside "if (var == null)" check after contract method invocation
      return normalException || ephemeralException && !normalOk;
    }

    boolean alwaysFails() {
      return (normalException || ephemeralException) && !normalOk;
    }
  }

  static class ArgResultEquality {
    final boolean argsEqual;
    final boolean firstArgEqualToResult;
    final boolean secondArgEqualToResult;

    ArgResultEquality(boolean argsEqual, boolean firstArgEqualToResult, boolean secondArgEqualToResult) {
      this.argsEqual = argsEqual;
      this.firstArgEqualToResult = firstArgEqualToResult;
      this.secondArgEqualToResult = secondArgEqualToResult;
    }

    ArgResultEquality merge(ArgResultEquality other) {
      return new ArgResultEquality(argsEqual && other.argsEqual, firstArgEqualToResult && other.firstArgEqualToResult,
                                   secondArgEqualToResult && other.secondArgEqualToResult);
    }

    boolean hasEquality() {
      return argsEqual || firstArgEqualToResult || secondArgEqualToResult;
    }
  }
}

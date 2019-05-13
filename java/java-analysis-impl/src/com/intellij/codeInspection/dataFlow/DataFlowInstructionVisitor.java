// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

final class DataFlowInstructionVisitor extends StandardInstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInstructionVisitor");
  private final Map<NullabilityProblemKind.NullabilityProblem<?>, StateInfo> myStateInfos = new LinkedHashMap<>();
  private final Set<TypeCastInstruction> myCCEInstructions = ContainerUtil.newHashSet();
  private final Map<PsiCallExpression, Boolean> myFailingCalls = new HashMap<>();
  private final Map<PsiExpression, ConstantResult> myConstantExpressions = new HashMap<>();
  private final Map<PsiElement, ThreeState> myOfNullableCalls = new HashMap<>();
  private final Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> myArrayStoreProblems = new HashMap<>();
  private final Map<PsiMethodReferenceExpression, DfaValue> myMethodReferenceResults = new HashMap<>();
  private final Map<PsiArrayAccessExpression, ThreeState> myOutOfBoundsArrayAccesses = new HashMap<>();
  private final Set<PsiElement> myReceiverMutabilityViolation = new HashSet<>();
  private final Set<PsiElement> myArgumentMutabilityViolation = new HashSet<>();
  private final Map<PsiExpression, Boolean> mySameValueAssigned = new HashMap<>();
  private final Map<PsiReferenceExpression, Boolean> mySameArguments = new HashMap<>();
  private boolean myAlwaysReturnsNotNull = true;
  private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();

  private static final CallMatcher USELESS_SAME_ARGUMENTS = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_MATH, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "min", "max").parameterCount(2),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "min", "max").parameterCount(2),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "replace").parameterCount(2)
  );

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiExpression left = instruction.getLExpression();
    if (left != null && !Boolean.FALSE.equals(mySameValueAssigned.get(left)) && !TypeUtils.isJavaLangString(left.getType())) {
      // Reporting strings is skipped because string reassignment might be intentionally used to deduplicate the heap objects
      // (we compare strings by contents)
      if (!left.isPhysical()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Non-physical element in assignment instruction: " + left.getParent().getText(), new Throwable());
        }
      } else {
        DfaValue value = memState.peek();
        DfaValue target = memState.getStackValue(1);
        if (target != null && memState.areEqual(value, target) &&
            !(value instanceof DfaConstValue && isFloatingZero(((DfaConstValue)value).getValue())) &&
            !isAssignmentToDefaultValueInConstructor(instruction, runner, target)) {
          mySameValueAssigned.merge(left, Boolean.TRUE, Boolean::logicalAnd);
        }
        else {
          mySameValueAssigned.put(left, Boolean.FALSE);
        }
      }
    }
    return super.visitAssign(instruction, runner, memState);
  }

  private static boolean isAssignmentToDefaultValueInConstructor(AssignInstruction instruction, DataFlowRunner runner, DfaValue target) {
    if (!(target instanceof DfaVariableValue)) return false;
    DfaVariableValue var = (DfaVariableValue)target;
    if (!(var.getPsiVariable() instanceof PsiField) || var.getQualifier() == null ||
        !(var.getQualifier().getDescriptor() instanceof DfaExpressionFactory.ThisDescriptor)) {
      return false;
    }

    // chained assignment like this.a = this.b = 0; is also supported
    PsiExpression rExpression = instruction.getRExpression();
    while (rExpression instanceof PsiAssignmentExpression &&
           ((PsiAssignmentExpression)rExpression).getOperationTokenType().equals(JavaTokenType.EQ)) {
      rExpression = ((PsiAssignmentExpression)rExpression).getRExpression();
    }
    if (rExpression == null) return false;
    DfaValue dest = runner.getFactory().createValue(rExpression);
    if (!(dest instanceof DfaConstValue)) return false;
    Object value = ((DfaConstValue)dest).getValue();

    PsiType type = var.getType();
    boolean isDefaultValue = Objects.equals(PsiTypesUtil.getDefaultValue(type), value) ||
                             Long.valueOf(0L).equals(value) && TypeConversionUtil.isIntegralNumberType(type);
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

  StreamEx<PsiReferenceExpression> pointlessSameArguments() {
    return StreamEx.ofKeys(mySameArguments, Boolean::booleanValue);
  }

  @Override
  protected void onInstructionProducesCCE(TypeCastInstruction instruction) {
    myCCEInstructions.add(instruction);
  }

  StreamEx<NullabilityProblemKind.NullabilityProblem<?>> problems() {
    // non-ephemeral NPE should be reported
    // ephemeral NPE should also be reported if only ephemeral states have reached a particular problematic instruction
    //  (e.g. if it's inside "if (var == null)" check after contract method invocation
    return StreamEx.ofKeys(myStateInfos, info -> info.normalNpe || info.ephemeralNpe && !info.normalOk);
  }

  public Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> getArrayStoreProblems() {
    return myArrayStoreProblems;
  }

  Map<PsiElement, ThreeState> getOfNullableCalls() {
    return myOfNullableCalls;
  }

  Map<PsiExpression, ConstantResult> getConstantExpressions() {
    return myConstantExpressions;
  }

  Map<PsiMethodReferenceExpression, DfaValue> getMethodReferenceResults() {
    return myMethodReferenceResults;
  }

  Set<TypeCastInstruction> getClassCastExceptionInstructions() {
    return myCCEInstructions;
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

  StreamEx<PsiCallExpression> alwaysFailingCalls() {
    return StreamEx.ofKeys(myFailingCalls, v -> v);
  }

  boolean isAlwaysReturnsNotNull(Instruction[] instructions) {
    return myAlwaysReturnsNotNull &&
           ContainerUtil.exists(instructions, i -> i instanceof ReturnInstruction && ((ReturnInstruction)i).getAnchor() instanceof PsiReturnStatement);
  }

  @Override
  protected void beforeExpressionPush(@NotNull DfaValue value,
                                      @NotNull PsiExpression expression,
                                      @Nullable TextRange range,
                                      @NotNull DfaMemoryState memState) {
    expression.accept(new ExpressionVisitor(value, memState));
    if (range == null) {
      reportConstantExpressionValue(value, memState, expression);
    }
  }

  @Override
  protected void beforeMethodCall(@NotNull PsiExpression expression,
                                  @NotNull DfaCallArguments arguments,
                                  @NotNull DataFlowRunner runner,
                                  @NotNull DfaMemoryState memState) {
    PsiReferenceExpression reference = USELESS_SAME_ARGUMENTS.getReferenceIfMatched(expression);
    if (reference != null) {
      mySameArguments.merge(reference, memState.areEqual(arguments.myArguments[0], arguments.myArguments[1]), Boolean::logicalAnd);
    }
  }

  @Override
  protected void beforeMethodReferenceResultPush(@NotNull DfaValue value,
                                                 @NotNull PsiMethodReferenceExpression methodRef,
                                                 @NotNull DfaMemoryState state) {
    if (OptionalUtil.OPTIONAL_OF_NULLABLE.methodReferenceMatches(methodRef)) {
      processOfNullableResult(value, state, methodRef.getReferenceNameElement());
    }
    PsiMethod method = tryCast(methodRef.resolve(), PsiMethod.class);
    if (method != null) {
      List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
      if (contracts.isEmpty() || !contracts.get(0).isTrivial()) {
        // Do not track if method reference may have different results
        myMethodReferenceResults.merge(methodRef, value, (a, b) -> a == b ? a : DfaUnknownValue.getInstance());
      }
    }
  }

  private void processOfNullableResult(@NotNull DfaValue value, @NotNull DfaMemoryState memState, PsiElement anchor) {
    DfaValueFactory factory = value.getFactory();
    DfaValue optionalValue = factory == null ? DfaUnknownValue.getInstance() : SpecialField.OPTIONAL_VALUE.createValue(factory, value);
    ThreeState present;
    if (memState.isNull(optionalValue)) {
      present = ThreeState.NO;
    }
    else if (memState.isNotNull(optionalValue)) {
      present = ThreeState.YES;
    }
    else present = ThreeState.UNSURE;
    myOfNullableCalls.merge(anchor, present, ThreeState::merge);
  }

  @Override
  protected void processArrayAccess(PsiArrayAccessExpression expression, boolean alwaysOutOfBounds) {
    myOutOfBoundsArrayAccesses.merge(expression, ThreeState.fromBoolean(alwaysOutOfBounds), ThreeState::merge);
  }

  @Override
  protected void processArrayStoreTypeMismatch(PsiAssignmentExpression assignmentExpression, PsiType fromType, PsiType toType) {
    if (assignmentExpression != null) {
      myArrayStoreProblems.put(assignmentExpression, Pair.create(fromType, toType));
    }
  }

  @Override
  public DfaInstructionState[] visitEndOfInitializer(EndOfInitializerInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    if (!instruction.isStatic()) {
      myEndOfInitializerStates.add(state.createCopy());
    }
    return super.visitEndOfInitializer(instruction, runner, state);
  }

  private static boolean hasNonTrivialFailingContracts(PsiCallExpression call) {
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
    return !contracts.isEmpty() &&
           contracts.stream().anyMatch(contract -> contract.getReturnValue().isFail() && !contract.isTrivial());
  }

  private void reportConstantExpressionValue(DfaValue value, DfaMemoryState memState, PsiExpression expression) {
    if (expression instanceof PsiLiteralExpression) return;
    ConstantResult curState = myConstantExpressions.get(expression);
    if (curState == ConstantResult.UNKNOWN) return;
    ConstantResult nextState = ConstantResult.UNKNOWN;
    DfaConstValue dfaConst = memState.getConstantValue(value);
    if (dfaConst != null) {
      nextState = ConstantResult.fromConstValue(dfaConst);
      if (curState != null && curState != nextState) {
        nextState = ConstantResult.UNKNOWN;
      }
    }
    myConstantExpressions.put(expression, nextState);
  }

  @Override
  protected boolean checkNotNullable(DfaMemoryState state, DfaValue value, @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null && problem.getKind() == NullabilityProblemKind.nullableReturn && !state.isNotNull(value)) {
      myAlwaysReturnsNotNull = false;
    }

    boolean ok = super.checkNotNullable(state, value, problem);
    if (problem == null) return ok;
    StateInfo info = myStateInfos.computeIfAbsent(problem, k -> new StateInfo());
    if (state.isEphemeral() && !ok) {
      info.ephemeralNpe = true;
    } else if (!state.isEphemeral()) {
      if (ok) info.normalOk = true;
      else info.normalNpe = true;
    }
    return ok;
  }

  @Override
  protected void reportMutabilityViolation(boolean receiver, @NotNull PsiElement anchor) {
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
    boolean ephemeralNpe;
    boolean normalNpe;
    boolean normalOk;
  }

  private class ExpressionVisitor extends JavaElementVisitor {
    private final DfaValue myValue;
    private final DfaMemoryState myMemState;

    ExpressionVisitor(DfaValue value, DfaMemoryState memState) {
      myValue = value;
      myMemState = memState;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (OptionalUtil.OPTIONAL_OF_NULLABLE.test(call)) {
        processOfNullableResult(myValue, myMemState, call.getArgumentList().getExpressions()[0]);
      }
    }

    @Override
    public void visitCallExpression(PsiCallExpression call) {
      super.visitCallExpression(call);
      Boolean isFailing = myFailingCalls.get(call);
      if (isFailing != null || hasNonTrivialFailingContracts(call)) {
        myFailingCalls.put(call, DfaConstValue.isContractFail(myValue) && !Boolean.FALSE.equals(isFailing));
      }
    }
  }

  enum ConstantResult {
    TRUE, FALSE, NULL, UNKNOWN;

    @NotNull
    @Override
    public String toString() {
      return name().toLowerCase(Locale.ENGLISH);
    }

    public Object value() {
      switch (this) {
        case TRUE:
          return Boolean.TRUE;
        case FALSE:
          return Boolean.FALSE;
        case NULL:
          return null;
        default:
          throw new UnsupportedOperationException();
      }
    }

    @NotNull
    static ConstantResult fromConstValue(@NotNull DfaConstValue constant) {
      Object value = constant.getValue();
      if (value == null) return NULL;
      if (Boolean.TRUE.equals(value)) return TRUE;
      if (Boolean.FALSE.equals(value)) return FALSE;
      return UNKNOWN;
    }
  }
}

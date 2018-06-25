// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

final class DataFlowInstructionVisitor extends StandardInstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInstructionVisitor");
  private final Map<NullabilityProblemKind.NullabilityProblem<?>, StateInfo> myStateInfos = new LinkedHashMap<>();
  private final Set<Instruction> myCCEInstructions = ContainerUtil.newHashSet();
  private final Map<PsiCallExpression, Boolean> myFailingCalls = new HashMap<>();
  private final Map<PsiExpression, ThreeState> myBooleanExpressions = new HashMap<>();
  private final Map<PsiElement, ThreeState> myOfNullableCalls = new HashMap<>();
  private final Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> myArrayStoreProblems = new HashMap<>();
  private final Map<PsiMethodReferenceExpression, DfaValue> myMethodReferenceResults = new HashMap<>();
  private final Map<PsiArrayAccessExpression, ThreeState> myOutOfBoundsArrayAccesses = new HashMap<>();
  private final Map<PsiReferenceExpression, DfaConstValue> myValues = new HashMap<>();
  private final Set<PsiElement> myReceiverMutabilityViolation = new HashSet<>();
  private final Set<PsiElement> myArgumentMutabilityViolation = new HashSet<>();
  private final Map<PsiExpression, Boolean> mySameValueAssigned = new HashMap<>();
  private boolean myAlwaysReturnsNotNull = true;
  private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiExpression left = instruction.getLExpression();
    if (left != null && !Boolean.FALSE.equals(mySameValueAssigned.get(left))) {
      if (!left.isPhysical()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Non-physical element in assignment instruction: " + left.getParent().getText(), new Throwable());
        }
      } else {
        DfaValue value = memState.peek();
        // Reporting of floating zero is skipped, because this produces false-positives on the code like
        // if(x == -0.0) x = 0.0;
        if (value instanceof DfaVariableValue || (value instanceof DfaConstValue && !isFloatingZero(((DfaConstValue)value).getValue()))) {
          DfaMemoryState copy = memState.createCopy();
          copy.pop();
          DfaValue target = copy.peek();
          boolean sameValue =
            !isAssignmentToDefaultValueInConstructor(instruction, runner, target) &&
            !copy.applyCondition(runner.getFactory().createCondition(value, DfaRelationValue.RelationType.NE, target));
          mySameValueAssigned.merge(left, sameValue, Boolean::logicalAnd);
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
        !(var.getQualifier().getSource() instanceof DfaExpressionFactory.ThisSource)) {
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

    PsiType type = var.getVariableType();
    boolean isDefaultValue = Objects.equals(PsiTypesUtil.getDefaultValue(type), value) || Long.valueOf(0L).equals(value) && PsiType.INT.equals(type);
    if (!isDefaultValue) return false;
    PsiMethod method = PsiTreeUtil.getParentOfType(rExpression, PsiMethod.class);
    return method != null && method.isConstructor();
  }

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

  Map<PsiExpression, ThreeState> getBooleanExpressions() {
    return myBooleanExpressions;
  }

  Map<PsiMethodReferenceExpression, DfaValue> getMethodReferenceResults() {
    return myMethodReferenceResults;
  }

  Set<Instruction> getClassCastExceptionInstructions() {
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
    handleBooleanResults(value, memState, expression);
  }

  @Override
  protected void beforeMethodReferenceResultPush(@NotNull DfaValue value,
                                                 @NotNull PsiMethodReferenceExpression methodRef,
                                                 @NotNull DfaMemoryState state) {
    if (DfaOptionalSupport.OPTIONAL_OF_NULLABLE.methodReferenceMatches(methodRef)) {
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
    Boolean fact = memState.getValueFact(value, DfaFactType.OPTIONAL_PRESENCE);
    ThreeState present = fact == null ? ThreeState.UNSURE : ThreeState.fromBoolean(fact);
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

  public Map<PsiReferenceExpression, DfaConstValue> getConstantReferenceValues() {
    return myValues;
  }

  private static boolean hasNonTrivialFailingContracts(PsiCallExpression call) {
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
    return !contracts.isEmpty() &&
           contracts.stream().anyMatch(contract -> contract.getReturnValue().isFail() && !contract.isTrivial());
  }

  private void handleBooleanResults(DfaValue value, DfaMemoryState memState, PsiExpression expression) {
    ThreeState curState = myBooleanExpressions.get(expression);
    if (curState == ThreeState.UNSURE) return;
    ThreeState nextState = ThreeState.UNSURE;
    value = value instanceof DfaVariableValue ? memState.getConstantValue((DfaVariableValue)value) : value;
    if (value instanceof DfaConstValue) {
      Object val = ((DfaConstValue)value).getValue();
      if (val instanceof Boolean) {
        nextState = ThreeState.fromBoolean((Boolean)val);
        if (curState != null && curState != nextState) {
          nextState = ThreeState.UNSURE;
        }
      }
    }
    if (curState != null || shouldCollectBooleanResult(expression)) {
      myBooleanExpressions.put(expression, nextState);
    }
  }

  private static boolean shouldCollectBooleanResult(PsiExpression expression) {
    if (expression instanceof PsiLiteralExpression) return false;
    PsiType type = expression.getType();
    if (type == null || !PsiType.BOOLEAN.isAssignableFrom(type)) return false;
    if (expression instanceof PsiPrefixExpression || expression instanceof PsiPolyadicExpression) {
      return !DataFlowInspectionBase.isFlagCheck(expression);
    }
    PsiPolyadicExpression polyadic = tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiPolyadicExpression.class);
    if (polyadic != null) {
      if ((polyadic.getOperationTokenType().equals(JavaTokenType.ANDAND) || polyadic.getOperationTokenType().equals(JavaTokenType.OROR)) &&
          !DataFlowInspectionBase.isFlagCheck(expression)) return true;
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (ExpressionUtils.isVoidContext(call)) return false;
      PsiMethod method = call.resolveMethod();
      if (method == null || !JavaMethodContractUtil.isPure(method)) return false;
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
      return CustomMethodHandlers.find(method) != null ||
             !contracts.isEmpty() &&
             contracts.stream().anyMatch(contract -> contract.getReturnValue().isBoolean() && !contract.isTrivial());
    }
    return false;
  }

  @Override
  protected boolean checkNotNullable(DfaMemoryState state, DfaValue value, @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (NullabilityProblemKind.nullableReturn.isMyProblem(problem) && !state.isNotNull(value)) {
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

  private static boolean shouldReportConstValue(Object value) {
    return value == null || value instanceof Boolean;
  }

  private static class StateInfo {
    boolean ephemeralNpe;
    boolean normalNpe;
    boolean normalOk;
  }

  private class ExpressionVisitor extends JavaElementVisitor {
    private final DfaValue myValue;
    private final DfaMemoryState myMemState;

    public ExpressionVisitor(DfaValue value, DfaMemoryState memState) {
      myValue = value;
      myMemState = memState;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (DfaOptionalSupport.OPTIONAL_OF_NULLABLE.test(call)) {
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

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      DfaConstValue oldValue = myValues.get(expression);
      if (DfaConstValue.isSentinel(oldValue)) return;
      if (myValue instanceof DfaVariableValue) {
        DfaConstValue constValue = myMemState.getConstantValue((DfaVariableValue)myValue);
        boolean report = constValue != null && shouldReportConstValue(constValue.getValue());
        if (!report) {
          constValue = null;
        }
        DfaConstValue newValue = constValue != null && (oldValue == null || oldValue == constValue)
                                 ? constValue
                                 : myValue.getFactory().getConstFactory().getSentinel();
        myValues.put(expression, newValue);
      }
    }
  }
}

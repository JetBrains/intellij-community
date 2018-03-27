// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

final class DataFlowInstructionVisitor extends StandardInstructionVisitor {
  private static final Object ANY_VALUE = new Object();
  private final Map<NullabilityProblemKind.NullabilityProblem<?>, StateInfo> myStateInfos = new LinkedHashMap<>();
  private final Set<Instruction> myCCEInstructions = ContainerUtil.newHashSet();
  private final Map<MethodCallInstruction, Boolean> myFailingCalls = new HashMap<>();
  private final Map<PsiMethodCallExpression, ThreeState> myOptionalCalls = new HashMap<>();
  private final Map<PsiMethodCallExpression, ThreeState> myBooleanCalls = new HashMap<>();
  private final Map<MethodCallInstruction, ThreeState> myOfNullableCalls = new HashMap<>();
  private final Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> myArrayStoreProblems = new HashMap<>();
  private final Map<PsiMethodReferenceExpression, DfaValue> myMethodReferenceResults = new HashMap<>();
  private final Map<PsiArrayAccessExpression, ThreeState> myOutOfBoundsArrayAccesses = new HashMap<>();
  private final List<PsiExpression> myOptionalQualifiers = new ArrayList<>();
  private final MultiMap<PushInstruction, Object> myPossibleVariableValues = MultiMap.createSet();
  private final Set<PsiElement> myReceiverMutabilityViolation = new HashSet<>();
  private final Set<PsiElement> myArgumentMutabilityViolation = new HashSet<>();
  private final Map<PsiExpression, Boolean> mySameValueAssigned = new HashMap<>();
  private boolean myAlwaysReturnsNotNull = true;

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiExpression left = instruction.getLExpression();
    if (left != null && !Boolean.FALSE.equals(mySameValueAssigned.get(left))) {
      DfaValue dest = memState.peek();
      // Reporting of floating zero is skipped, because this produces false-positives on the code like
      // if(x == -0.0) x = 0.0;
      if (dest instanceof DfaVariableValue || (dest instanceof DfaConstValue && !isFloatingZero(((DfaConstValue)dest).getValue()))) {
        DfaMemoryState copy = memState.createCopy();
        copy.pop();
        DfaValue src = copy.peek();
        boolean sameValue = !copy.applyCondition(runner.getFactory().createCondition(dest, DfaRelationValue.RelationType.NE, src));
        mySameValueAssigned.merge(left, sameValue, Boolean::logicalAnd);
      }
      else {
        mySameValueAssigned.put(left, Boolean.FALSE);
      }
    }
    return super.visitAssign(instruction, runner, memState);
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

  Map<PsiMethodCallExpression, ThreeState> getOptionalCalls() {
    return myOptionalCalls;
  }

  Map<MethodCallInstruction, ThreeState> getOfNullableCalls() {
    return myOfNullableCalls;
  }

  Map<PsiMethodCallExpression, ThreeState> getBooleanCalls() {
    return myBooleanCalls;
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

  Stream<PsiArrayAccessExpression> outOfBoundsArrayAccesses() {
    return StreamEx.ofKeys(myOutOfBoundsArrayAccesses, ThreeState.YES::equals);
  }

  List<PsiExpression> getOptionalQualifiers() {
    return myOptionalQualifiers;
  }

  Map<PsiCall, List<MethodContract>> getAlwaysFailingCalls() {
    return StreamEx.ofKeys(myFailingCalls, v -> v)
      .mapToEntry(MethodCallInstruction::getCallExpression, MethodCallInstruction::getContracts).toMap();
  }

  boolean isAlwaysReturnsNotNull(Instruction[] instructions) {
    return myAlwaysReturnsNotNull &&
           ContainerUtil.exists(instructions, i -> i instanceof ReturnInstruction && ((ReturnInstruction)i).getAnchor() instanceof PsiReturnStatement);
  }

  @Override
  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction,
                                               DataFlowRunner runner,
                                               DfaMemoryState memState) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(instruction.getCallExpression(), PsiMethodCallExpression.class);
    if (call != null) {
      String methodName = call.getMethodExpression().getReferenceName();
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
      if (qualifier != null && TypeUtils.isOptional(qualifier.getType())) {
        if ("isPresent".equals(methodName) && qualifier instanceof PsiMethodCallExpression) {
          myOptionalQualifiers.add(qualifier);
        }
        else if (DfaOptionalSupport.isOptionalGetMethodName(methodName)) {
          Boolean fact = memState.getValueFact(memState.peek(), DfaFactType.OPTIONAL_PRESENCE);
          ThreeState state = fact == null ? ThreeState.UNSURE : ThreeState.fromBoolean(fact);
          myOptionalCalls.merge(call, state, ThreeState::merge);
        }
      }
    }
    if (instruction.matches(DfaOptionalSupport.OPTIONAL_OF_NULLABLE)) {
      DfaValue arg = memState.peek();
      ThreeState nullArg = memState.isNull(arg) ? ThreeState.YES : memState.isNotNull(arg) ? ThreeState.NO : ThreeState.UNSURE;
      myOfNullableCalls.merge(instruction, nullArg, ThreeState::merge);
    }
    DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
    if (hasNonTrivialFailingContracts(instruction)) {
      DfaConstValue fail = runner.getFactory().getConstFactory().getContractFail();
      boolean allFail = Arrays.stream(states).allMatch(s -> s.getMemoryState().peek() == fail);
      myFailingCalls.merge(instruction, allFail, Boolean::logicalAnd);
    }
    handleBooleanCalls(instruction, states);
    return states;
  }

  void handleBooleanCalls(MethodCallInstruction instruction, DfaInstructionState[] states) {
    if (!hasNonTrivialBooleanContracts(instruction)) return;
    PsiMethod method = instruction.getTargetMethod();
    if (method == null || !ControlFlowAnalyzer.isPure(method)) return;
    PsiMethodCallExpression call = ObjectUtils.tryCast(instruction.getCallExpression(), PsiMethodCallExpression.class);
    if (call == null || myBooleanCalls.get(call) == ThreeState.UNSURE) return;
    PsiElement parent = call.getParent();
    if (parent instanceof PsiExpressionStatement) return;
    if (parent instanceof PsiLambdaExpression &&
        PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent))) {
      return;
    }
    for (DfaInstructionState s : states) {
      DfaValue val = s.getMemoryState().peek();
      ThreeState state = ThreeState.UNSURE;
      if (val instanceof DfaConstValue) {
        Object value = ((DfaConstValue)val).getValue();
        if (value instanceof Boolean) {
          state = ThreeState.fromBoolean((Boolean)value);
        }
      }
      myBooleanCalls.merge(call, state, ThreeState::merge);
    }
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
  protected void processMethodReferenceResult(PsiMethodReferenceExpression methodRef,
                                              List<? extends MethodContract> contracts,
                                              DfaValue res) {
    if(contracts.isEmpty() || !contracts.get(0).isTrivial()) {
      // Do not track if method reference may have different results
      myMethodReferenceResults.merge(methodRef, res, (a, b) -> a == b ? a : DfaUnknownValue.getInstance());
    }
  }

  @Override
  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiExpression place = instruction.getPlace();
    if (!instruction.isReferenceWrite() && place instanceof PsiReferenceExpression) {
      DfaValue dfaValue = instruction.getValue();
      if (dfaValue instanceof DfaVariableValue) {
        DfaConstValue constValue = memState.getConstantValue((DfaVariableValue)dfaValue);
        boolean report = constValue != null && shouldReportConstValue(constValue.getValue());
        myPossibleVariableValues.putValue(instruction, report ? constValue : ANY_VALUE);
      }
    }
    return super.visitPush(instruction, runner, memState);
  }

  public List<Pair<PsiReferenceExpression, DfaConstValue>> getConstantReferenceValues() {
    List<Pair<PsiReferenceExpression, DfaConstValue>> result = ContainerUtil.newArrayList();
    for (PushInstruction instruction : myPossibleVariableValues.keySet()) {
      Collection<Object> values = myPossibleVariableValues.get(instruction);
      if (values.size() == 1) {
        Object singleValue = values.iterator().next();
        if (singleValue != ANY_VALUE) {
          result.add(Pair.create((PsiReferenceExpression)instruction.getPlace(), (DfaConstValue)singleValue));
        }
      }
    }
    return result;
  }

  private static boolean hasNonTrivialFailingContracts(MethodCallInstruction instruction) {
    List<MethodContract> contracts = instruction.getContracts();
    return !contracts.isEmpty() && contracts.stream().anyMatch(
      contract -> contract.getReturnValue() == MethodContract.ValueConstraint.THROW_EXCEPTION && !contract.isTrivial());
  }

  private static boolean hasNonTrivialBooleanContracts(MethodCallInstruction instruction) {
    if (CustomMethodHandlers.find(instruction) != null) return true;
    List<MethodContract> contracts = instruction.getContracts();
    return !contracts.isEmpty() && contracts.stream().anyMatch(
      contract -> (contract.getReturnValue() == MethodContract.ValueConstraint.FALSE_VALUE ||
                   contract.getReturnValue() == MethodContract.ValueConstraint.TRUE_VALUE)
                  && !contract.isTrivial());
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
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.JavaTokenType.*;

/**
 * @author peter
 */
public class StandardInstructionVisitor extends InstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.StandardInstructionVisitor");
  private static final Object ANY_VALUE = new Object();
  private final Set<BinopInstruction> myReachable = new THashSet<>();
  private final Set<BinopInstruction> myCanBeNullInInstanceof = new THashSet<>();
  private final MultiMap<PushInstruction, Object> myPossibleVariableValues = MultiMap.createSet();
  private final Set<PsiElement> myNotToReportReachability = new THashSet<>();
  private final Set<InstanceofInstruction> myUsefulInstanceofs = new THashSet<>();
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<MethodCallInstruction, Nullness> myReturnTypeNullability = new FactoryMap<MethodCallInstruction, Nullness>() {
    @Override
    protected Nullness create(MethodCallInstruction key) {
      final PsiCall callExpression = key.getCallExpression();
      if (callExpression instanceof PsiNewExpression) {
        return Nullness.NOT_NULL;
      }

      return callExpression != null ? DfaPsiUtil.getElementNullability(key.getResultType(), callExpression.resolveMethod()) : null;
    }
  };

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;

      final PsiModifierListOwner psi = var.getPsiVariable();
      boolean forceDeclaredNullity = !(psi instanceof PsiParameter && psi.getParent() instanceof PsiParameterList);
      if (forceDeclaredNullity && var.getInherentNullability() == Nullness.NOT_NULL) {
        checkNotNullable(memState, dfaSource, NullabilityProblem.assigningToNotNull, instruction.getRExpression());
      }
      if (!(psi instanceof PsiField) || !psi.hasModifierProperty(PsiModifier.VOLATILE)) {
        memState.setVarValue(var, dfaSource);
      }
      if (var.getInherentNullability() == Nullness.NULLABLE && !memState.isNotNull(dfaSource) && instruction.isVariableInitializer()) {
        DfaMemoryStateImpl stateImpl = (DfaMemoryStateImpl)memState;
        stateImpl.setVariableState(var, stateImpl.getVariableState(var).withNullability(Nullness.NULLABLE));
      }

    } else if (dfaDest instanceof DfaTypeValue && ((DfaTypeValue)dfaDest).isNotNull()) {
      checkNotNullable(memState, dfaSource, NullabilityProblem.assigningToNotNull, instruction.getRExpression());
    }

    memState.push(dfaDest);

    return nextInstruction(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState memState) {
    final DfaValue retValue = memState.pop();
    checkNotNullable(memState, retValue, NullabilityProblem.nullableReturn, instruction.getReturn());
    return nextInstruction(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue qualifier = memState.pop();
    if (!checkNotNullable(memState, qualifier, NullabilityProblem.fieldAccessNPE, instruction.getElementToAssert())) {
      forceNotNull(runner, memState, qualifier);
    }

    return nextInstruction(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    if (!instruction.isReferenceWrite() && instruction.getPlace() instanceof PsiReferenceExpression) {
      DfaValue dfaValue = instruction.getValue();
      if (dfaValue instanceof DfaVariableValue) {
        DfaConstValue constValue = memState.getConstantValue((DfaVariableValue)dfaValue);
        myPossibleVariableValues.putValue(instruction, constValue != null && (constValue.getValue() == null || constValue.getValue() instanceof Boolean) ? constValue : ANY_VALUE);
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

  @Override
  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValueFactory factory = runner.getFactory();
    DfaValue dfaExpr = factory.createValue(instruction.getCasted());
    if (dfaExpr != null) {
      DfaTypeValue dfaType = (DfaTypeValue)factory.createTypeValue(instruction.getCastTo(), Nullness.UNKNOWN);
      DfaRelationValue dfaInstanceof = factory.getRelationFactory().createRelation(dfaExpr, dfaType, INSTANCEOF_KEYWORD, false);
      if (dfaInstanceof != null && !memState.applyInstanceofOrNull(dfaInstanceof)) {
        onInstructionProducesCCE(instruction);
      }
    }

    if (instruction.getCastTo() instanceof PsiPrimitiveType) {
      memState.push(runner.getFactory().getBoxedFactory().createUnboxed(memState.pop()));
    }

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesCCE(TypeCastInstruction instruction) {}

  @Override
  public DfaInstructionState[] visitMethodCall(final MethodCallInstruction instruction, final DataFlowRunner runner, final DfaMemoryState memState) {
    DfaValue[] argValues = popCallArguments(instruction, runner, memState);
    final DfaValue qualifier = popQualifier(instruction, runner, memState);

    LinkedHashSet<DfaMemoryState> currentStates = ContainerUtil.newLinkedHashSet(memState);
    Set<DfaMemoryState> finalStates = ContainerUtil.newLinkedHashSet();
    if (argValues != null) {
      for (MethodContract contract : instruction.getContracts()) {
        currentStates = addContractResults(argValues, contract, currentStates, instruction, runner.getFactory(), finalStates);
        if (currentStates.size() + finalStates.size() > DataFlowRunner.MAX_STATES_PER_BRANCH) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Too complex contract on " + instruction.getContext() + ", skipping contract processing");
          }
          finalStates.clear();
          currentStates = ContainerUtil.newLinkedHashSet(memState);
          break;
        }
      }
    }
    for (DfaMemoryState state : currentStates) {
      state.push(getMethodResultValue(instruction, qualifier, runner.getFactory()));
      finalStates.add(state);
    }

    DfaInstructionState[] result = new DfaInstructionState[finalStates.size()];
    int i = 0;
    for (DfaMemoryState state : finalStates) {
      if (instruction.shouldFlushFields()) {
        state.flushFields();
      }
      result[i++] = new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), state);
    }
    return result;
  }

  @Nullable 
  private DfaValue[] popCallArguments(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final PsiExpression[] args = instruction.getArgs();

    PsiMethod method = instruction.getTargetMethod();
    boolean varargCall = instruction.isVarArgCall();
    DfaValue[] argValues;
    if (method == null || instruction.getContracts().isEmpty()) {
      argValues = null;
    } else {
      PsiParameterList paramList = method.getParameterList();
      int paramCount = paramList.getParametersCount();
      if (paramCount == args.length || method.isVarArgs() && args.length >= paramCount - 1) {
        argValues = new DfaValue[paramCount];
        if (varargCall) {
          argValues[paramCount - 1] = runner.getFactory().createTypeValue(paramList.getParameters()[paramCount - 1].getType(), Nullness.NOT_NULL);
        }
      } else {
        argValues = null;
      }
    }

    for (int i = 0; i < args.length; i++) {
      final DfaValue arg = memState.pop();
      int paramIndex = args.length - i - 1;
      if (argValues != null && (paramIndex < argValues.length - 1 || !varargCall)) {
        argValues[paramIndex] = arg;
      }

      PsiExpression expr = args[paramIndex];
      Nullness requiredNullability = instruction.getArgRequiredNullability(expr);
      if (requiredNullability == Nullness.NOT_NULL) {
        if (!checkNotNullable(memState, arg, NullabilityProblem.passingNullableToNotNullParameter, expr)) {
          forceNotNull(runner, memState, arg);
        }
      }
      else if (!instruction.updateOfNullable(memState, arg) && requiredNullability == Nullness.UNKNOWN) {
        checkNotNullable(memState, arg, NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter, expr);
      }
    }
    return argValues;
  }

  private DfaValue popQualifier(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    @NotNull final DfaValue qualifier = memState.pop();
    boolean unboxing = instruction.getMethodType() == MethodCallInstruction.MethodType.UNBOXING;
    NullabilityProblem problem = unboxing ? NullabilityProblem.unboxingNullable : NullabilityProblem.callNPE;
    PsiElement anchor = unboxing ? instruction.getContext() : instruction.getCallExpression();
    if (!checkNotNullable(memState, qualifier, problem, anchor)) {
      forceNotNull(runner, memState, qualifier);
    }
    return qualifier;
  }

  private LinkedHashSet<DfaMemoryState> addContractResults(DfaValue[] argValues,
                                                  MethodContract contract,
                                                  LinkedHashSet<DfaMemoryState> states,
                                                  MethodCallInstruction instruction,
                                                  DfaValueFactory factory,
                                                  Set<DfaMemoryState> finalStates) {
    DfaConstValue.Factory constFactory = factory.getConstFactory();
    LinkedHashSet<DfaMemoryState> falseStates = ContainerUtil.newLinkedHashSet();
    for (int i = 0; i < argValues.length; i++) {
      DfaValue argValue = argValues[i];
      MethodContract.ValueConstraint constraint = contract.arguments[i];
      DfaConstValue expectedValue = constraint.getComparisonValue(factory);
      if (expectedValue == null) continue;

      boolean nullContract = expectedValue == constFactory.getNull();
      boolean invertCondition = constraint.shouldUseNonEqComparison();
      DfaValue condition = factory.getRelationFactory().createRelation(argValue, expectedValue, EQEQ, invertCondition);
      if (condition == null) {
        if (!(argValue instanceof DfaConstValue)) {
          for (DfaMemoryState state : states) {
            DfaMemoryState falseCopy = state.createCopy();
            if (nullContract) {
              (invertCondition ? falseCopy : state).markEphemeral();
            }
            falseStates.add(falseCopy);
          }
          continue;
        }
        condition = constFactory.createFromValue((argValue == expectedValue) != invertCondition, PsiType.BOOLEAN, null);
      }

      LinkedHashSet<DfaMemoryState> nextStates = ContainerUtil.newLinkedHashSet();
      for (DfaMemoryState state : states) {
        boolean unknownVsNull = nullContract &&
                                argValue instanceof DfaVariableValue &&
                                ((DfaMemoryStateImpl)state).getVariableState((DfaVariableValue)argValue).getNullability() == Nullness.UNKNOWN;
        DfaMemoryState falseCopy = state.createCopy();
        if (state.applyCondition(condition)) {
          if (unknownVsNull && !invertCondition) {
            state.markEphemeral();
          }
          nextStates.add(state);
        }
        if (falseCopy.applyCondition(condition.createNegated())) {
          if (unknownVsNull && invertCondition) {
            falseCopy.markEphemeral();
          }
          falseStates.add(falseCopy);
        }
      }
      states = nextStates;
    }

    for (DfaMemoryState state : states) {
      state.push(getDfaContractReturnValue(contract, instruction, factory));
      finalStates.add(state);
    }
    
    return falseStates;
  }

  private DfaValue getDfaContractReturnValue(MethodContract contract,
                                             MethodCallInstruction instruction,
                                             DfaValueFactory factory) {
    switch (contract.returnValue) {
      case NULL_VALUE: return factory.getConstFactory().getNull();
      case NOT_NULL_VALUE: return factory.createTypeValue(instruction.getResultType(), Nullness.NOT_NULL);
      case TRUE_VALUE: return factory.getConstFactory().getTrue();
      case FALSE_VALUE: return factory.getConstFactory().getFalse();
      case THROW_EXCEPTION: return factory.getConstFactory().getContractFail();
      default: return getMethodResultValue(instruction, null, factory);
    }
  }

  private static void forceNotNull(DataFlowRunner runner, DfaMemoryState memState, DfaValue arg) {
    if (arg instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)arg;
      memState.setVarValue(var, runner.getFactory().createTypeValue(var.getVariableType(), Nullness.NOT_NULL));
    }
  }

  @NotNull
  private DfaValue getMethodResultValue(MethodCallInstruction instruction, @Nullable DfaValue qualifierValue, DfaValueFactory factory) {
    DfaValue precalculated = instruction.getPrecalculatedReturnValue();
    if (precalculated != null) {
      return precalculated;
    }

    final PsiType type = instruction.getResultType();
    final MethodCallInstruction.MethodType methodType = instruction.getMethodType();

    if (methodType == MethodCallInstruction.MethodType.UNBOXING) {
      return factory.getBoxedFactory().createUnboxed(qualifierValue);
    }

    if (methodType == MethodCallInstruction.MethodType.BOXING) {
      DfaValue boxed = factory.getBoxedFactory().createBoxed(qualifierValue);
      return boxed == null ? factory.createTypeValue(type, Nullness.NOT_NULL) : boxed;
    }

    if (methodType == MethodCallInstruction.MethodType.CAST) {
      assert qualifierValue != null;
      if (qualifierValue instanceof DfaConstValue) {
        Object casted = TypeConversionUtil.computeCastTo(((DfaConstValue)qualifierValue).getValue(), type);
        return factory.getConstFactory().createFromValue(casted, type, ((DfaConstValue)qualifierValue).getConstant());
      }
      return qualifierValue;
    }

    if (type != null && (type instanceof PsiClassType || type.getArrayDimensions() > 0)) {
      Nullness nullability = myReturnTypeNullability.get(instruction);
      if (nullability == Nullness.UNKNOWN && factory.isUnknownMembersAreNullable()) {
        nullability = Nullness.NULLABLE;
      }
      return factory.createTypeValue(type, nullability);
    }
    return DfaUnknownValue.getInstance();
  }

  protected boolean checkNotNullable(DfaMemoryState state,
                                     DfaValue value, NullabilityProblem problem,
                                     PsiElement anchor) {
    boolean notNullable = state.checkNotNullable(value);
    if (notNullable &&
        problem != NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter) {
      DfaValueFactory factory = ((DfaMemoryStateImpl)state).getFactory();
      state.applyCondition(factory.getRelationFactory().createRelation(value, factory.getConstFactory().getNull(), NE, false));
    }
    return notNullable;
  }

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    myReachable.add(instruction);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final IElementType opSign = instruction.getOperationSign();
    if (opSign != null) {
      DfaInstructionState[] states = handleConstantComparison(instruction, runner, memState, dfaRight, dfaLeft, opSign);
      if (states == null) {
        states = handleRelationBinop(instruction, runner, memState, dfaRight, dfaLeft);
      }
      if (states != null) {
        return states;
      }

      if (PLUS == opSign) {
        memState.push(instruction.getNonNullStringValue(runner.getFactory()));
      }
      else {
        if (instruction instanceof InstanceofInstruction) {
          handleInstanceof((InstanceofInstruction)instruction, dfaRight, dfaLeft);
        }
        memState.push(DfaUnknownValue.getInstance());
      }
    }
    else {
      memState.push(DfaUnknownValue.getInstance());
    }

    instruction.setTrueReachable();  // Not a branching instruction actually.
    instruction.setFalseReachable();

    return nextInstruction(instruction, runner, memState);
  }

  @Nullable
  private DfaInstructionState[] handleRelationBinop(BinopInstruction instruction,
                                                    DataFlowRunner runner,
                                                    DfaMemoryState memState,
                                                    DfaValue dfaRight, DfaValue dfaLeft) {
    DfaValueFactory factory = runner.getFactory();
    final Instruction next = runner.getInstruction(instruction.getIndex() + 1);
    DfaRelationValue dfaRelation = factory.getRelationFactory().createRelation(dfaLeft, dfaRight, instruction.getOperationSign(), false);
    if (dfaRelation == null) {
      return null;
    }

    myCanBeNullInInstanceof.add(instruction);

    ArrayList<DfaInstructionState> states = new ArrayList<>();

    final DfaMemoryState trueCopy = memState.createCopy();
    if (trueCopy.applyCondition(dfaRelation)) {
      trueCopy.push(factory.getConstFactory().getTrue());
      instruction.setTrueReachable();
      states.add(new DfaInstructionState(next, trueCopy));
    }

    //noinspection UnnecessaryLocalVariable
    DfaMemoryState falseCopy = memState;
    if (falseCopy.applyCondition(dfaRelation.createNegated())) {
      falseCopy.push(factory.getConstFactory().getFalse());
      instruction.setFalseReachable();
      states.add(new DfaInstructionState(next, falseCopy));
      if (instruction instanceof InstanceofInstruction && !falseCopy.isNull(dfaLeft)) {
        myUsefulInstanceofs.add((InstanceofInstruction)instruction);
      }
    }

    return states.toArray(new DfaInstructionState[states.size()]);
  }

  public void skipConstantConditionReporting(@Nullable PsiElement anchor) {
    ContainerUtil.addIfNotNull(myNotToReportReachability, anchor);
  }

  private void handleInstanceof(InstanceofInstruction instruction, DfaValue dfaRight, DfaValue dfaLeft) {
    if (dfaLeft instanceof DfaTypeValue && dfaRight instanceof DfaTypeValue) {
      if (!((DfaTypeValue)dfaLeft).isNotNull()) {
        myCanBeNullInInstanceof.add(instruction);
      }

      if (((DfaTypeValue)dfaRight).getDfaType().isAssignableFrom(((DfaTypeValue)dfaLeft).getDfaType())) {
        return;
      }
    }
    myUsefulInstanceofs.add(instruction);
  }

  @Nullable
  private static DfaInstructionState[] handleConstantComparison(BinopInstruction instruction,
                                                                DataFlowRunner runner,
                                                                DfaMemoryState memState,
                                                                DfaValue dfaRight,
                                                                DfaValue dfaLeft, IElementType opSign) {
    if (dfaRight instanceof DfaConstValue && dfaLeft instanceof DfaVariableValue) {
      Object value = ((DfaConstValue)dfaRight).getValue();
      if (value instanceof Number) {
        DfaInstructionState[] result = checkComparingWithConstant(instruction, runner, memState, (DfaVariableValue)dfaLeft, opSign,
                                                                  (Number)value);
        if (result != null) {
          return result;
        }
      }
    }
    if (dfaRight instanceof DfaVariableValue && dfaLeft instanceof DfaConstValue) {
      return handleConstantComparison(instruction, runner, memState, dfaLeft, dfaRight, DfaRelationValue.getSymmetricOperation(opSign));
    }

    if (EQEQ != opSign && NE != opSign) {
      return null;
    }

    if (dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaConstValue ||
        dfaLeft == runner.getFactory().getConstFactory().getContractFail() ||
        dfaRight == runner.getFactory().getConstFactory().getContractFail()) {
      boolean negated = (NE == opSign) ^ (DfaMemoryStateImpl.isNaN(dfaLeft) || DfaMemoryStateImpl.isNaN(dfaRight));
      if (dfaLeft == dfaRight ^ negated) {
        return alwaysTrue(instruction, runner, memState);
      }
      return alwaysFalse(instruction, runner, memState);
    }

    return null;
  }

  @Nullable
  private static DfaInstructionState[] checkComparingWithConstant(BinopInstruction instruction,
                                                                  DataFlowRunner runner,
                                                                  DfaMemoryState memState,
                                                                  DfaVariableValue var,
                                                                  IElementType opSign, Number comparedWith) {
    DfaConstValue knownConstantValue = memState.getConstantValue(var);
    Object knownValue = knownConstantValue == null ? null : knownConstantValue.getValue();
    if (knownValue instanceof Number) {
      return checkComparisonWithKnownRange(instruction, runner, memState, opSign, comparedWith, (Number)knownValue, (Number)knownValue);
    }

    PsiType varType = var.getVariableType();
    if (!(varType instanceof PsiPrimitiveType)) return null;
    
    if (PsiType.FLOAT.equals(varType) || PsiType.DOUBLE.equals(varType)) return null;

    double minValue = PsiType.BYTE.equals(varType) ? Byte.MIN_VALUE : PsiType.SHORT.equals(varType)
                                                                 ? Short.MIN_VALUE : PsiType.INT.equals(varType)
                                                                   ? Integer.MIN_VALUE : PsiType.CHAR.equals(varType) ? Character.MIN_VALUE :
                                                                                         Long.MIN_VALUE;
    double maxValue = PsiType.BYTE.equals(varType) ? Byte.MAX_VALUE : PsiType.SHORT.equals(varType)
                                                                 ? Short.MAX_VALUE : PsiType.INT.equals(varType)
                                                                   ? Integer.MAX_VALUE : PsiType.CHAR.equals(varType) ? Character.MAX_VALUE :
                                                                                         Long.MAX_VALUE;

    return checkComparisonWithKnownRange(instruction, runner, memState, opSign, comparedWith, minValue, maxValue);
  }

  private static int compare(Number a, Number b) {
    long aLong = a.longValue();
    long bLong = b.longValue();
    if (aLong != bLong) return aLong > bLong ? 1 : -1;

    return Double.compare(a.doubleValue(), b.doubleValue());
  }

  @Nullable
  private static DfaInstructionState[] checkComparisonWithKnownRange(BinopInstruction instruction,
                                                                     DataFlowRunner runner,
                                                                     DfaMemoryState memState,
                                                                     IElementType opSign,
                                                                     Number comparedWith,
                                                                     Number rangeMin,
                                                                     Number rangeMax) {
    if (compare(comparedWith, rangeMin) < 0 || compare(comparedWith, rangeMax) > 0) {
      if (opSign == EQEQ) return alwaysFalse(instruction, runner, memState);
      if (opSign == NE) return alwaysTrue(instruction, runner, memState);
    }

    if (opSign == LT && compare(comparedWith, rangeMin) <= 0) return alwaysFalse(instruction, runner, memState);
    if (opSign == LT && compare(comparedWith, rangeMax) > 0) return alwaysTrue(instruction, runner, memState);
    if (opSign == LE && compare(comparedWith, rangeMax) >= 0) return alwaysTrue(instruction, runner, memState);
    if (opSign == LE && compare(comparedWith, rangeMin) < 0) return alwaysFalse(instruction, runner, memState);

    if (opSign == GT && compare(comparedWith, rangeMax) >= 0) return alwaysFalse(instruction, runner, memState);
    if (opSign == GT && compare(comparedWith, rangeMin) < 0) return alwaysTrue(instruction, runner, memState);
    if (opSign == GE && compare(comparedWith, rangeMin) <= 0) return alwaysTrue(instruction, runner, memState);
    if (opSign == GE && compare(comparedWith, rangeMax) > 0) return alwaysFalse(instruction, runner, memState);

    return null;
  }

  private static DfaInstructionState[] alwaysFalse(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.push(runner.getFactory().getConstFactory().getFalse());
    instruction.setFalseReachable();
    return nextInstruction(instruction, runner, memState);
  }

  private static DfaInstructionState[] alwaysTrue(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.push(runner.getFactory().getConstFactory().getTrue());
    instruction.setTrueReachable();
    return nextInstruction(instruction, runner, memState);
  }

  public boolean isInstanceofRedundant(InstanceofInstruction instruction) {
    return !myUsefulInstanceofs.contains(instruction) && !instruction.isConditionConst() && myReachable.contains(instruction);
  }

  public boolean canBeNull(BinopInstruction instruction) {
    return myCanBeNullInInstanceof.contains(instruction);
  }

  public boolean silenceConstantCondition(@Nullable PsiElement element) {
    for (PsiElement skipped : myNotToReportReachability) {
      if (PsiTreeUtil.isAncestor(element, skipped, false)) {
        return true;
      }
    }
    return false;
  }

}

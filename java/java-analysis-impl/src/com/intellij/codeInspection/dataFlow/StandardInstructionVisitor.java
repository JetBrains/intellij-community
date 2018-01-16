/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author peter
 */
public class StandardInstructionVisitor extends InstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.StandardInstructionVisitor");

  private final Set<BinopInstruction> myReachable = new THashSet<>();
  private final Set<BinopInstruction> myCanBeNullInInstanceof = new THashSet<>();
  private final Set<InstanceofInstruction> myUsefulInstanceofs = new THashSet<>();

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (!(dfaDest instanceof DfaVariableValue) && instruction.getAssignedValue() != null) {
      // It's possible that dfaDest on the stack is cleared to DfaFactMapValue due to variable flush
      // (e.g. during StateMerger#mergeByFacts), so we try to restore the original destination.
      dfaDest = instruction.getAssignedValue();
    }

    PsiExpression lValue = PsiUtil.skipParenthesizedExprDown(instruction.getLExpression());
    PsiExpression rValue = instruction.getRExpression();
    NullabilityProblemKind<PsiExpression> kind;
    if (lValue instanceof PsiArrayAccessExpression) {
      kind = NullabilityProblemKind.storingToNotNullArray;
      checkArrayElementAssignability(runner, memState, dfaSource, lValue, rValue);
    }
    else {
      kind = NullabilityProblemKind.assigningToNotNull;
    }

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;

      final PsiModifierListOwner psi = var.getPsiVariable();
      boolean forceDeclaredNullity = !(psi instanceof PsiParameter && psi.getParent() instanceof PsiParameterList);
      if (forceDeclaredNullity && var.getInherentNullability() == Nullness.NOT_NULL) {
        checkNotNullable(memState, dfaSource, kind.problem(rValue));
      }
      if (!(psi instanceof PsiField) || !psi.hasModifierProperty(PsiModifier.VOLATILE)) {
        memState.setVarValue(var, dfaSource);
      }
      if (var.getInherentNullability() == Nullness.NULLABLE && !memState.isNotNull(dfaSource) && instruction.isVariableInitializer()) {
        DfaMemoryStateImpl stateImpl = (DfaMemoryStateImpl)memState;
        stateImpl.setVariableState(var, stateImpl.getVariableState(var).withFact(DfaFactType.CAN_BE_NULL, true));
      }
    } else if (dfaDest instanceof DfaFactMapValue && Boolean.FALSE.equals(((DfaFactMapValue)dfaDest).get(DfaFactType.CAN_BE_NULL))) {
      checkNotNullable(memState, dfaSource, kind.problem(rValue));
    }

    memState.push(dfaDest);
    flushArrayOnUnknownAssignment(instruction, runner.getFactory(), dfaDest, memState);

    return nextInstruction(instruction, runner, memState);
  }

  private void checkArrayElementAssignability(DataFlowRunner runner,
                                              DfaMemoryState memState,
                                              DfaValue dfaSource,
                                              PsiExpression lValue,
                                              PsiExpression rValue) {
    if (rValue == null) return;
    PsiType rCodeType = rValue.getType();
    PsiType lCodeType = lValue.getType();
    // If types known from source are not convertible, a compilation error is displayed, additional warning is unnecessary
    if (rCodeType == null || lCodeType == null || !TypeConversionUtil.areTypesConvertible(rCodeType, lCodeType)) return;
    PsiExpression array = ((PsiArrayAccessExpression)lValue).getArrayExpression();
    DfaValue arrayValue = runner.getFactory().createValue(array);
    PsiType arrayType = getType(array, arrayValue, memState);
    if (!(arrayType instanceof PsiArrayType)) return;
    PsiType componentType = ((PsiArrayType)arrayType).getComponentType();
    PsiType sourceType = getType(rValue, dfaSource, memState);
    if (sourceType == null || TypeConversionUtil.areTypesConvertible(sourceType, componentType)) return;
    PsiAssignmentExpression assignmentExpression =
      PsiTreeUtil.getParentOfType(rValue, PsiAssignmentExpression.class);
    processArrayStoreTypeMismatch(assignmentExpression, sourceType, componentType);
  }

  @Nullable
  private static PsiType getType(@Nullable PsiExpression expression, @Nullable DfaValue value, @NotNull DfaMemoryState memState) {
    TypeConstraint fact = value == null ? null : memState.getValueFact(value, DfaFactType.TYPE_CONSTRAINT);
    PsiType type = fact == null ? null : fact.getPsiType();
    if (type != null) return type;
    return expression == null ? null : expression.getType();
  }

  protected void processArrayStoreTypeMismatch(PsiAssignmentExpression assignmentExpression, PsiType fromType, PsiType toType) {
  }

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState memState) {
    final DfaValue retValue = memState.pop();
    checkNotNullable(memState, retValue, NullabilityProblemKind.nullableReturn.problem(instruction.getReturn()));
    return nextInstruction(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitArrayAccess(ArrayAccessInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiArrayAccessExpression arrayExpression = instruction.getExpression();
    DfaValue index = memState.pop();
    DfaValue array = dereference(memState, memState.pop(), NullabilityProblemKind.arrayAccessNPE.problem(arrayExpression));
    boolean alwaysOutOfBounds = false;
    DfaValueFactory factory = runner.getFactory();
    if (index != DfaUnknownValue.getInstance()) {
      DfaValue indexNonNegative = factory.createCondition(index, RelationType.GE, factory.getInt(0));
      if (!memState.applyCondition(indexNonNegative)) {
        alwaysOutOfBounds = true;
      }
      DfaValue dfaLength = SpecialField.ARRAY_LENGTH.createValue(factory, array);
      if(dfaLength != null) {
        DfaValue indexLessThanLength = factory.createCondition(index, RelationType.LT, dfaLength);
        if (!memState.applyCondition(indexLessThanLength)) {
          alwaysOutOfBounds = true;
        }
      }
    }
    processArrayAccess(arrayExpression, alwaysOutOfBounds);

    DfaValue result = instruction.getValue();
    LongRangeSet rangeSet = memState.getValueFact(index, DfaFactType.RANGE);
    if (rangeSet != null && !rangeSet.isEmpty() && rangeSet.min() == rangeSet.max()) {
      long longIdx = rangeSet.min();
      if(longIdx >= 0 && longIdx <= Integer.MAX_VALUE) {
        int intIdx = (int)longIdx;
        DfaValue arrayElementValue = runner.getFactory().getExpressionFactory().getArrayElementValue(array, intIdx);
        if (arrayElementValue != null) {
          result = arrayElementValue;
        }
      }
    }
    memState.push(result);
    return nextInstruction(instruction, runner, memState);
  }

  protected void processArrayAccess(PsiArrayAccessExpression expression, boolean alwaysOutOfBounds) {

  }

  @Override
  public DfaInstructionState[] visitFieldReference(DereferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiExpression expression = instruction.getExpression();
    final DfaValue qualifier = dereference(memState, memState.pop(), NullabilityProblemKind.fieldAccessNPE.problem(expression));
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiMethodReferenceExpression) {
      handleMethodReference(qualifier, (PsiMethodReferenceExpression)parent, runner, memState);
    }

    return nextInstruction(instruction, runner, memState);
  }

  private void handleMethodReference(DfaValue qualifier,
                                     PsiMethodReferenceExpression methodRef,
                                     DataFlowRunner runner,
                                     DfaMemoryState state) {
    PsiType functionalInterfaceType = methodRef.getFunctionalInterfaceType();
    if (functionalInterfaceType == null) return;
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (sam == null || PsiType.VOID.equals(sam.getReturnType())) return;
    JavaResolveResult resolveResult = methodRef.advancedResolve(false);
    PsiMethod method = ObjectUtils.tryCast(resolveResult.getElement(), PsiMethod.class);
    if (method == null || !ControlFlowAnalyzer.isPure(method)) return;
    List<? extends MethodContract> contracts = ControlFlowAnalyzer.getMethodCallContracts(method, null);
    if (contracts.isEmpty()) return;
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    DfaCallArguments callArguments = getMethodReferenceCallArguments(methodRef, qualifier, runner, sam, method, substitutor);
    PsiType returnType = substitutor.substitute(method.getReturnType());
    DfaValue defaultResult = runner.getFactory().createTypeValue(returnType, DfaPsiUtil.getElementNullability(returnType, method));
    Stream<DfaValue> returnValues = possibleReturnValues(callArguments, state, contracts, runner.getFactory(), defaultResult);
    returnValues.forEach(res -> processMethodReferenceResult(methodRef, contracts, res));
  }

  @NotNull
  private static DfaCallArguments getMethodReferenceCallArguments(PsiMethodReferenceExpression methodRef,
                                                                  DfaValue qualifier,
                                                                  DataFlowRunner runner,
                                                                  PsiMethod sam,
                                                                  PsiMethod method,
                                                                  PsiSubstitutor substitutor) {
    PsiParameter[] samParameters = sam.getParameterList().getParameters();
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    boolean instanceBound = !isStatic && !PsiMethodReferenceUtil.isStaticallyReferenced(methodRef);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    DfaValue[] arguments = new DfaValue[parameters.length];
    Arrays.fill(arguments, DfaUnknownValue.getInstance());
    for (int i = 0; i < samParameters.length; i++) {
      DfaValue value = runner.getFactory()
        .createTypeValue(substitutor.substitute(samParameters[i].getType()), DfaPsiUtil.getFunctionalParameterNullability(methodRef, i));
      if (i == 0 && !isStatic && !instanceBound) {
        qualifier = value;
      }
      else {
        int idx = i - ((isStatic || instanceBound) ? 0 : 1);
        if (idx >= arguments.length) break;
        if (!(parameters[idx].getType() instanceof PsiEllipsisType)) {
          arguments[idx] = value;
        }
      }
    }
    return new DfaCallArguments(qualifier, arguments);
  }

  private static Stream<DfaValue> possibleReturnValues(DfaCallArguments callArguments,
                                                       DfaMemoryState state,
                                                       List<? extends MethodContract> contracts,
                                                       DfaValueFactory factory, DfaValue defaultResult) {
    LinkedHashSet<DfaMemoryState> currentStates = ContainerUtil.newLinkedHashSet(state.createClosureState());
    Set<DfaMemoryState> finalStates = ContainerUtil.newLinkedHashSet();
    for (MethodContract contract : contracts) {
      DfaValue result = contract.getDfaReturnValue(factory, defaultResult);
      currentStates = addContractResults(callArguments, contract, currentStates, factory, finalStates, result);
    }
    return StreamEx.of(finalStates).map(DfaMemoryState::peek)
      .append(currentStates.isEmpty() ? StreamEx.empty() : StreamEx.of(defaultResult)).distinct();
  }

  protected void processMethodReferenceResult(PsiMethodReferenceExpression methodRef,
                                              List<? extends MethodContract> contracts,
                                              DfaValue res) {
  }

  @Override
  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiType type = instruction.getCastTo();
    final DfaValueFactory factory = runner.getFactory();
    PsiType fromType = instruction.getCasted().getType();
    if (fromType != null && type.isConvertibleFrom(fromType) && !memState.castTopOfStack(factory.createDfaType(type))) {
      onInstructionProducesCCE(instruction);
    }

    if (type instanceof PsiPrimitiveType) {
      memState.push(factory.getBoxedFactory().createUnboxed(memState.pop()));
    }

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesCCE(TypeCastInstruction instruction) {}

  @Override
  public DfaInstructionState[] visitMethodCall(final MethodCallInstruction instruction, final DataFlowRunner runner, final DfaMemoryState memState) {
    Set<DfaMemoryState> finalStates = ContainerUtil.newLinkedHashSet();
    finalStates.addAll(handleKnownMethods(instruction, runner, memState));

    if (finalStates.isEmpty()) {
      DfaCallArguments callArguments = popCall(instruction, runner, memState, true);

      LinkedHashSet<DfaMemoryState> currentStates = ContainerUtil.newLinkedHashSet(memState);
      DfaValue resultValue = getMethodResultValue(instruction, callArguments.myQualifier, memState, runner.getFactory());
      if (callArguments.myArguments != null) {
        for (MethodContract contract : instruction.getContracts()) {
          DfaValue returnValue = contract.getDfaReturnValue(runner.getFactory(), resultValue);
          currentStates = addContractResults(callArguments, contract, currentStates, runner.getFactory(), finalStates, returnValue);
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
        state.push(resultValue);
        finalStates.add(state);
      }
    }

    PsiMethodReferenceExpression methodRef = instruction.getMethodType() == MethodCallInstruction.MethodType.METHOD_REFERENCE_CALL ?
                                             (PsiMethodReferenceExpression)instruction.getContext() : null;
    DfaInstructionState[] result = new DfaInstructionState[finalStates.size()];
    int i = 0;
    for (DfaMemoryState state : finalStates) {
      if (instruction.shouldFlushFields()) {
        state.flushFields();
      }
      if (methodRef != null) {
        processMethodReferenceResult(methodRef, instruction.getContracts(), state.peek());
      }
      result[i++] = new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), state);
    }
    return result;
  }

  @NotNull
  private List<DfaMemoryState> handleKnownMethods(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    CustomMethodHandlers.CustomMethodHandler handler = CustomMethodHandlers.find(instruction);
    if (handler == null) return Collections.emptyList();
    memState = memState.createCopy();
    DfaCallArguments callArguments = popCall(instruction, runner, memState, false);
    return callArguments.myArguments == null ? Collections.emptyList() :
         handler.handle(callArguments, memState, runner.getFactory());
  }

  @NotNull
  private DfaCallArguments popCall(MethodCallInstruction instruction,
                                   DataFlowRunner runner,
                                   DfaMemoryState memState,
                                   boolean contractOnly) {
    PsiMethod method = instruction.getTargetMethod();
    MutationSignature sig = MutationSignature.fromMethod(method);
    DfaValue[] argValues = popCallArguments(instruction, runner, memState, contractOnly, sig);
    final DfaValue qualifier = popQualifier(instruction, memState, sig);
    return new DfaCallArguments(qualifier, argValues);
  }

  @Nullable
  private DfaValue[] popCallArguments(MethodCallInstruction instruction,
                                      DataFlowRunner runner,
                                      DfaMemoryState memState,
                                      boolean contractOnly, MutationSignature sig) {
    final int argCount = instruction.getArgCount();

    PsiMethod method = instruction.getTargetMethod();
    boolean varargCall = instruction.isVarArgCall();
    DfaValue[] argValues;
    if (method == null || (contractOnly && instruction.getContracts().isEmpty())) {
      argValues = null;
    } else {
      PsiParameterList paramList = method.getParameterList();
      int paramCount = paramList.getParametersCount();
      if (paramCount == argCount || method.isVarArgs() && argCount >= paramCount - 1) {
        argValues = new DfaValue[paramCount];
        if (varargCall) {
          argValues[paramCount - 1] = runner.getFactory().createTypeValue(paramList.getParameters()[paramCount - 1].getType(), Nullness.NOT_NULL);
        }
      } else {
        argValues = null;
      }
    }

    for (int i = 0; i < argCount; i++) {
      DfaValue arg = memState.pop();
      int paramIndex = argCount - i - 1;

      PsiElement anchor = instruction.getArgumentAnchor(paramIndex);
      Nullness requiredNullability = instruction.getArgRequiredNullability(paramIndex);
      if (requiredNullability == Nullness.NOT_NULL) {
        arg = dereference(memState, arg, NullabilityProblemKind.passingNullableToNotNullParameter.problem(anchor));
      }
      else if (requiredNullability == Nullness.UNKNOWN) {
        checkNotNullable(memState, arg, NullabilityProblemKind.passingNullableArgumentToNonAnnotatedParameter.problem(anchor));
      }
      if (sig.mutatesArg(paramIndex) && !memState.applyFact(arg, DfaFactType.MUTABILITY, Mutability.MUTABLE)) {
        reportMutabilityViolation(false, anchor);
        if (arg instanceof DfaVariableValue) {
          memState.forceVariableFact((DfaVariableValue)arg, DfaFactType.MUTABILITY, Mutability.MUTABLE);
        }
      }
      if (argValues != null && (paramIndex < argValues.length - 1 || !varargCall)) {
        argValues[paramIndex] = arg;
      }
    }
    return argValues;
  }

  protected void reportMutabilityViolation(boolean receiver, @NotNull PsiElement anchor) {
  }

  private DfaValue popQualifier(MethodCallInstruction instruction,
                                DfaMemoryState memState,
                                MutationSignature sig) {
    DfaValue value = dereference(memState, memState.pop(), instruction.getQualifierNullabilityProblem());
    if (sig.mutatesThis() && !memState.applyFact(value, DfaFactType.MUTABILITY, Mutability.MUTABLE)) {
      reportMutabilityViolation(true, instruction.getContext());
      if (value instanceof DfaVariableValue) {
        memState.forceVariableFact((DfaVariableValue)value, DfaFactType.MUTABILITY, Mutability.MUTABLE);
      }
    }
    return value;
  }

  private static LinkedHashSet<DfaMemoryState> addContractResults(DfaCallArguments callArguments,
                                                                  MethodContract contract,
                                                                  LinkedHashSet<DfaMemoryState> states,
                                                                  DfaValueFactory factory,
                                                                  Set<DfaMemoryState> finalStates,
                                                                  DfaValue returnValue) {
    List<DfaValue> conditions = ContainerUtil.map(contract.getConditions(), cv -> cv.makeDfaValue(factory, callArguments));
    if (StreamEx.of(conditions).allMatch(factory.getConstFactory().getTrue()::equals)) {
      for (DfaMemoryState state : states) {
        state.push(returnValue);
        finalStates.add(state);
      }
      return new LinkedHashSet<>();
    }
    if (StreamEx.of(conditions).has(factory.getConstFactory().getFalse())) {
      return states;
    }

    LinkedHashSet<DfaMemoryState> falseStates = ContainerUtil.newLinkedHashSet();
    LinkedHashSet<DfaMemoryState> trueStates = ContainerUtil.newLinkedHashSet();

    for (DfaMemoryState state : states) {
      for (DfaValue condition : conditions) {
        if (condition == null) {
          condition = DfaUnknownValue.getInstance();
        }
        DfaMemoryState falseState = state.createCopy();
        if (falseState.applyContractCondition(condition.createNegated())) {
          falseStates.add(falseState);
        }
        if (!state.applyContractCondition(condition)) {
          state = null;
          break;
        }
      }
      if(state != null) {
        trueStates.add(state);
      }
    }

    for (DfaMemoryState state : trueStates) {
      state.push(returnValue);
      finalStates.add(state);
    }
    
    return falseStates;
  }

  private <T extends PsiElement> DfaValue dereference(DfaMemoryState memState,
                                                      DfaValue value,
                                                      @Nullable NullabilityProblemKind.NullabilityProblem<T> problem) {
    if (checkNotNullable(memState, value, problem)) return value;
    if (value instanceof DfaFactMapValue) {
      return ((DfaFactMapValue)value).withFact(DfaFactType.CAN_BE_NULL, false);
    }
    if (memState.isNull(value) && NullabilityProblemKind.nullableFunctionReturn.isMyProblem(problem)) {
      return value.getFactory().getFactValue(DfaFactType.CAN_BE_NULL, false);
    }
    if (value instanceof DfaVariableValue) {
      memState.forceVariableFact((DfaVariableValue)value, DfaFactType.CAN_BE_NULL, false);
    }
    return value;
  }

  @NotNull
  private static PsiMethod findSpecificMethod(@NotNull PsiMethod method, @NotNull DfaMemoryState state, @Nullable DfaValue qualifier) {
    if (qualifier == null || !PsiUtil.canBeOverridden(method)) return method;
    TypeConstraint constraint = state.getValueFact(qualifier, DfaFactType.TYPE_CONSTRAINT);
    PsiType type = constraint == null ? null : constraint.getPsiType();
    return MethodUtils.findSpecificMethod(method, type);
  }

  @NotNull
  private static DfaValue getMethodResultValue(MethodCallInstruction instruction,
                                               @Nullable DfaValue qualifierValue,
                                               DfaMemoryState state, DfaValueFactory factory) {
    DfaValue precalculated = instruction.getPrecalculatedReturnValue();
    if (precalculated != null) {
      return precalculated;
    }

    PsiType type = instruction.getResultType();
    final MethodCallInstruction.MethodType methodType = instruction.getMethodType();

    if (methodType == MethodCallInstruction.MethodType.METHOD_REFERENCE_CALL && qualifierValue instanceof DfaVariableValue) {
      PsiMethod method = instruction.getTargetMethod();
      for (SpecialField sf : SpecialField.values()) {
        if (sf.isMyAccessor(method)) {
          return sf.createValue(factory, qualifierValue);
        }
      }
      PsiModifierListOwner modifierListOwner = DfaExpressionFactory.getAccessedVariableOrGetter(method);
      if (modifierListOwner != null) {
        return factory.getVarFactory().createVariableValue(modifierListOwner, instruction.getResultType(), false,
                                                           (DfaVariableValue)qualifierValue);
      }
    }

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

    if (type != null && !(type instanceof PsiPrimitiveType)) {
      Nullness nullability = instruction.getReturnNullability();
      PsiMethod targetMethod = instruction.getTargetMethod();
      Mutability mutable = Mutability.UNKNOWN;
      if (targetMethod != null) {
        mutable = Mutability.getMutability(targetMethod);
        PsiMethod realMethod = findSpecificMethod(targetMethod, state, qualifierValue);
        if (realMethod != targetMethod) {
          nullability = DfaPsiUtil.getElementNullability(type, realMethod);
          mutable = Mutability.getMutability(realMethod);
          PsiType returnType = realMethod.getReturnType();
          if (returnType != null && TypeConversionUtil.erasure(type).isAssignableFrom(returnType)) {
            // possibly covariant return type
            type = returnType;
          }
        }
        if (nullability == Nullness.UNKNOWN) {
          nullability = factory.suggestNullabilityForNonAnnotatedMember(targetMethod);
        }
      }
      DfaValue value = factory.createTypeValue(type, nullability);
      return factory.withFact(value, DfaFactType.MUTABILITY, mutable);
    }
    LongRangeSet range = LongRangeSet.fromType(type);
    if (range != null) {
      PsiCall call = instruction.getCallExpression();
      if (call instanceof PsiMethodCallExpression) {
        range = range.intersect(LongRangeSet.fromPsiElement(call.resolveMethod()));
      }
      return factory.getFactValue(DfaFactType.RANGE, range);
    }
    return DfaUnknownValue.getInstance();
  }

  protected boolean checkNotNullable(DfaMemoryState state, DfaValue value, @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    boolean notNullable = state.checkNotNullable(value);
    if (notNullable && !NullabilityProblemKind.passingNullableArgumentToNonAnnotatedParameter.isMyProblem(problem)) {
      DfaValueFactory factory = ((DfaMemoryStateImpl)state).getFactory();
      state.applyCondition(factory.createCondition(value, RelationType.NE, factory.getConstFactory().getNull()));
    }
    return notNullable;
  }

  @Override
  public DfaInstructionState[] visitCheckNotNull(CheckNotNullInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue result = dereference(memState, memState.pop(), instruction.getProblem());
    memState.push(result);
    return super.visitCheckNotNull(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    myReachable.add(instruction);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final IElementType opSign = instruction.getOperationSign();
    RelationType relationType = RelationType.fromElementType(opSign);
    if (relationType != null) {
      DfaInstructionState[] states = handleConstantComparison(instruction, runner, memState, dfaRight, dfaLeft, relationType);
      if (states == null) {
        states = handleRelationBinop(instruction, runner, memState, dfaRight, dfaLeft, relationType);
      }
      if (states != null) {
        return states;
      }
    }
    DfaValue result = null;
    if (JavaTokenType.AND == opSign) {
      LongRangeSet left = memState.getValueFact(dfaLeft, DfaFactType.RANGE);
      LongRangeSet right = memState.getValueFact(dfaRight, DfaFactType.RANGE);
      if(left != null && right != null) {
        result = runner.getFactory().getFactValue(DfaFactType.RANGE, left.bitwiseAnd(right));
      }
    }
    else if (JavaTokenType.PERC == opSign) {
      LongRangeSet left = memState.getValueFact(dfaLeft, DfaFactType.RANGE);
      LongRangeSet right = memState.getValueFact(dfaRight, DfaFactType.RANGE);
      if(left != null && right != null) {
        result = runner.getFactory().getFactValue(DfaFactType.RANGE, left.mod(right));
      }
    }
    else if (JavaTokenType.PLUS == opSign) {
      PsiElement expr = instruction.getPsiAnchor();
      PsiType type = expr instanceof PsiExpression ? ((PsiExpression)expr).getType() : null;
      if(PsiType.INT.equals(type) || PsiType.LONG.equals(type)) {
        LongRangeSet left = memState.getValueFact(dfaLeft, DfaFactType.RANGE);
        LongRangeSet right = memState.getValueFact(dfaRight, DfaFactType.RANGE);
        if(left != null && right != null) {
          result = runner.getFactory().getFactValue(DfaFactType.RANGE, left.plus(right, PsiType.LONG.equals(type)));
        }
      } else {
        result = instruction.getNonNullStringValue(runner.getFactory());
      }
    }
    else if (JavaTokenType.MINUS == opSign) {
      PsiElement expr = instruction.getPsiAnchor();
      PsiType type = expr instanceof PsiExpression ? ((PsiExpression)expr).getType() : null;
      if (PsiType.INT.equals(type) || PsiType.LONG.equals(type)) {
        LongRangeSet left = memState.getValueFact(dfaLeft, DfaFactType.RANGE);
        LongRangeSet right = memState.getValueFact(dfaRight, DfaFactType.RANGE);
        if (left != null && right != null) {
          result = runner.getFactory().getFactValue(DfaFactType.RANGE, left.minus(right, PsiType.LONG.equals(type)));
        }
      }
    }
    else {
      if (instruction instanceof InstanceofInstruction) {
        handleInstanceof((InstanceofInstruction)instruction, dfaRight, dfaLeft);
      }
    }
    memState.push(result == null ? DfaUnknownValue.getInstance() : result);

    instruction.setTrueReachable();  // Not a branching instruction actually.
    instruction.setFalseReachable();

    return nextInstruction(instruction, runner, memState);
  }

  @Nullable
  private DfaInstructionState[] handleRelationBinop(BinopInstruction instruction,
                                                    DataFlowRunner runner,
                                                    DfaMemoryState memState,
                                                    DfaValue dfaRight,
                                                    DfaValue dfaLeft,
                                                    RelationType relationType) {
    DfaValueFactory factory = runner.getFactory();
    final Instruction next = runner.getInstruction(instruction.getIndex() + 1);

    RelationType[] relations = splitRelation(relationType);

    ArrayList<DfaInstructionState> states = new ArrayList<>(relations.length);

    for (int i = 0; i < relations.length; i++) {
      RelationType relation = relations[i];
      DfaValue condition = factory.createCondition(dfaLeft, relation, dfaRight);
      if (condition instanceof DfaUnknownValue) return null;
      if (condition instanceof DfaConstValue && Boolean.FALSE.equals(((DfaConstValue)condition).getValue())) {
        continue;
      }
      final DfaMemoryState copy = i == relations.length - 1 ? memState : memState.createCopy();
      if (copy.applyCondition(condition)) {
        boolean isTrue = relationType.isSubRelation(relation);
        copy.push(factory.getBoolean(isTrue));
        if (isTrue) {
          instruction.setTrueReachable();
        }
        else {
          if (instruction instanceof InstanceofInstruction && !copy.isNull(dfaLeft)) {
            myUsefulInstanceofs.add((InstanceofInstruction)instruction);
          }
          instruction.setFalseReachable();
        }
        states.add(new DfaInstructionState(next, copy));
      }
    }
    myCanBeNullInInstanceof.add(instruction);

    return states.toArray(new DfaInstructionState[states.size()]);
  }

  @NotNull
  private static RelationType[] splitRelation(RelationType relationType) {
    switch (relationType) {
      case LT:
      case LE:
      case GT:
      case GE:
        return new RelationType[]{RelationType.LT, RelationType.GT, RelationType.EQ};
      default:
        return new RelationType[]{relationType, relationType.getNegated()};
    }
  }

  private void handleInstanceof(InstanceofInstruction instruction, DfaValue dfaRight, DfaValue dfaLeft) {
    if (dfaLeft instanceof DfaFactMapValue && dfaRight instanceof DfaFactMapValue) {
      DfaFactMapValue left = (DfaFactMapValue)dfaLeft;
      DfaFactMapValue right = (DfaFactMapValue)dfaRight;

      if (!Boolean.FALSE.equals(left.get(DfaFactType.CAN_BE_NULL))) {
        myCanBeNullInInstanceof.add(instruction);
      }

      if (right.getFacts().with(DfaFactType.CAN_BE_NULL, null).isSuperStateOf(left.getFacts())) {
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
                                                                DfaValue dfaLeft, RelationType relationType) {
    if (dfaLeft instanceof DfaVariableValue && dfaRight instanceof DfaVariableValue) {
      Number leftValue = getKnownNumberValue(memState, (DfaVariableValue)dfaLeft);
      Number rightValue = getKnownNumberValue(memState, (DfaVariableValue)dfaRight);
      if (leftValue != null && rightValue != null) {
        return checkComparisonWithKnownValue(instruction, runner, memState, relationType, leftValue, rightValue);
      }
    }

    if (dfaRight instanceof DfaConstValue && dfaLeft instanceof DfaVariableValue) {
      Object value = ((DfaConstValue)dfaRight).getValue();
      if (value instanceof Number) {
        DfaInstructionState[] result = checkComparingWithConstant(instruction, runner, memState, (DfaVariableValue)dfaLeft, relationType,
                                                                  (Number)value);
        if (result != null) {
          return result;
        }
      }
    }
    if (dfaRight instanceof DfaVariableValue && dfaLeft instanceof DfaConstValue) {
      return handleConstantComparison(instruction, runner, memState, dfaLeft, dfaRight, relationType.getFlipped());
    }

    if (relationType != RelationType.EQ && relationType != RelationType.NE) {
      return null;
    }

    if (dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaConstValue ||
        dfaLeft == runner.getFactory().getConstFactory().getContractFail() ||
        dfaRight == runner.getFactory().getConstFactory().getContractFail()) {
      boolean negated = (relationType == RelationType.NE) ^ (DfaMemoryStateImpl.isNaN(dfaLeft) || DfaMemoryStateImpl.isNaN(dfaRight));
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
                                                                  RelationType opSign, Number comparedWith) {
    Number knownValue = getKnownNumberValue(memState, var);
    if (knownValue != null) {
      return checkComparisonWithKnownValue(instruction, runner, memState, opSign, knownValue, comparedWith);
    }
    return null;
  }

  @Nullable
  private static Number getKnownNumberValue(DfaMemoryState memState, DfaVariableValue var) {
    DfaConstValue knownConstantValue = memState.getConstantValue(var);
    return knownConstantValue != null && knownConstantValue.getValue() instanceof Number ? (Number)knownConstantValue.getValue() : null;
  }

  private static DfaInstructionState[] checkComparisonWithKnownValue(BinopInstruction instruction,
                                                                     DataFlowRunner runner,
                                                                     DfaMemoryState memState,
                                                                     RelationType opSign,
                                                                     Number leftValue,
                                                                     Number rightValue) {
    int cmp = compare(leftValue, rightValue);
    Boolean result = null;
    boolean hasNaN = DfaUtil.isNaN(leftValue) || DfaUtil.isNaN(rightValue);
    if (cmp < 0 || cmp > 0) {
      if(opSign == RelationType.EQ) result = false;
      else if (opSign == RelationType.NE) result = true;
    }
    if (opSign == RelationType.LT) {
      result = !hasNaN && cmp < 0;
    }
    else if (opSign == RelationType.GT) {
      result = !hasNaN && cmp > 0;
    }
    else if (opSign == RelationType.LE) {
      result = !hasNaN && cmp <= 0;
    }
    else if (opSign == RelationType.GE) {
      result = !hasNaN && cmp >= 0;
    }
    if (result == null) {
      return null;
    }
    return result ? alwaysTrue(instruction, runner, memState) : alwaysFalse(instruction, runner, memState);
  }

  private static int compare(Number a, Number b) {
    long aLong = a.longValue();
    long bLong = b.longValue();
    if (aLong != bLong) return aLong > bLong ? 1 : -1;

    return Double.compare(a.doubleValue(), b.doubleValue());
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
}

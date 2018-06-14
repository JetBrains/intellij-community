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

import com.intellij.codeInsight.Nullability;
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
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class StandardInstructionVisitor extends InstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.StandardInstructionVisitor");

  private final Set<InstanceofInstruction> myReachable = new THashSet<>();
  private final Set<InstanceofInstruction> myCanBeNullInInstanceof = new THashSet<>();
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
    if (dfaSource == dfaDest) {
      memState.push(dfaDest);
      return nextInstruction(instruction, runner, memState);
    }
    dropLocality(dfaSource, memState);

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

      PsiModifierListOwner psi = var.getPsiVariable();
      boolean forceDeclaredNullity = !(psi instanceof PsiParameter && psi.getParent() instanceof PsiParameterList);
      if (psi instanceof PsiField && !psi.hasModifierProperty(PsiModifier.FINAL) && var.getInherentNullability() == Nullability.UNKNOWN) {
        checkNotNullable(memState, dfaSource, NullabilityProblemKind.assigningNullableValueToNonAnnotatedField.problem(rValue));        
      }
      else if (forceDeclaredNullity && var.getInherentNullability() == Nullability.NOT_NULL) {
        checkNotNullable(memState, dfaSource, kind.problem(rValue));
      }
      if (dfaSource instanceof DfaFactMapValue &&
          var.getQualifier() != null &&
          !Boolean.TRUE.equals(memState.getValueFact(var.getQualifier(), DfaFactType.LOCALITY))) {
        dfaSource = ((DfaFactMapValue)dfaSource).withFact(DfaFactType.LOCALITY, null);
      }
      if (!(psi instanceof PsiField) || !psi.hasModifierProperty(PsiModifier.VOLATILE)) {
        memState.setVarValue(var, dfaSource);
      }
      if (var.getInherentNullability() == Nullability.NULLABLE && !memState.isNotNull(dfaSource) && instruction.isVariableInitializer()) {
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
  public DfaInstructionState[] visitEscapeInstruction(EscapeInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    instruction.getEscapedVars().forEach(var -> dropLocality(var, state));
    return super.visitEscapeInstruction(instruction, runner, state);
  }

  private static void dropLocality(DfaValue value, DfaMemoryState state) {
    if (!(value instanceof DfaVariableValue)) return;
    DfaVariableValue var = (DfaVariableValue)value;
    state.dropFact(var, DfaFactType.LOCALITY);
    for (DfaVariableValue v : new ArrayList<>(var.getDependentVariables())) {
      state.dropFact(v, DfaFactType.LOCALITY);
    }
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
    DfaValue arrayElementValue =
      runner.getFactory().getExpressionFactory().getArrayElementValue(array, rangeSet == null ? LongRangeSet.all() : rangeSet);
    if (arrayElementValue != DfaUnknownValue.getInstance()) {
      result = arrayElementValue;
    }
    pushExpressionResult(result, instruction, memState);
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
      dropLocality(qualifier, memState);
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
    if (method == null || !JavaMethodContractUtil.isPure(method)) return;
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    DfaCallArguments callArguments = getMethodReferenceCallArguments(methodRef, qualifier, runner, sam, method, substitutor);
    dereference(state, callArguments.myQualifier, NullabilityProblemKind.callMethodRefNPE.problem(methodRef));
    if (contracts.isEmpty()) return;
    PsiType returnType = substitutor.substitute(method.getReturnType());
    DfaValue defaultResult = runner.getFactory().createTypeValue(returnType, DfaPsiUtil.getElementNullability(returnType, method));
    Set<DfaCallState> currentStates = Collections.singleton(new DfaCallState(state.createClosureState(), callArguments));
    for (MethodContract contract : contracts) {
      currentStates = addContractResults(contract, currentStates, runner.getFactory(), new HashSet<>(), defaultResult, methodRef);
    }
    for (DfaCallState currentState: currentStates) {
      pushExpressionResult(defaultResult, () -> methodRef, currentState.myMemoryState);
    }
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
    return new DfaCallArguments(qualifier, arguments, JavaMethodContractUtil.isPure(method));
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

      Set<DfaCallState> currentStates = Collections.singleton(new DfaCallState(memState, callArguments));
      DfaValue defaultResult = getMethodResultValue(instruction, callArguments.myQualifier, memState, runner.getFactory());
      if (callArguments.myArguments != null) {
        for (MethodContract contract : instruction.getContracts()) {
          currentStates = addContractResults(contract, currentStates, runner.getFactory(), finalStates, defaultResult, instruction.getExpression());
          if (currentStates.size() + finalStates.size() > DataFlowRunner.MAX_STATES_PER_BRANCH) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Too complex contract on " + instruction.getContext() + ", skipping contract processing");
            }
            finalStates.clear();
            currentStates = Collections.singleton(new DfaCallState(memState, callArguments));
            break;
          }
        }
      }
      for (DfaCallState callState : currentStates) {
        pushExpressionResult(defaultResult, instruction, callState.myMemoryState);
        finalStates.add(callState.myMemoryState);
      }
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

  @NotNull
  private List<DfaMemoryState> handleKnownMethods(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    if (instruction.getTargetMethod() == null) return Collections.emptyList();
    CustomMethodHandlers.CustomMethodHandler handler = CustomMethodHandlers.find(instruction);
    if (handler == null) return Collections.emptyList();
    memState = memState.createCopy();
    DfaCallArguments callArguments = popCall(instruction, runner, memState, false);
    DfaValue result = callArguments.myArguments == null ? null : handler.getMethodResult(callArguments, memState, runner.getFactory());
    if (result != null) {
      pushExpressionResult(result, instruction, memState);
      return Collections.singletonList(memState);
    }
    return Collections.emptyList();
  }

  @NotNull
  protected DfaCallArguments popCall(MethodCallInstruction instruction,
                                     DataFlowRunner runner,
                                     DfaMemoryState memState,
                                     boolean contractOnly) {
    PsiMethod method = instruction.getTargetMethod();
    MutationSignature sig = MutationSignature.fromMethod(method);
    DfaValue[] argValues = popCallArguments(instruction, runner, memState, contractOnly, sig);
    final DfaValue qualifier = popQualifier(instruction, memState, sig);
    return new DfaCallArguments(qualifier, argValues, !instruction.shouldFlushFields());
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
          argValues[paramCount - 1] =
            runner.getFactory().createTypeValue(paramList.getParameters()[paramCount - 1].getType(), Nullability.NOT_NULL);
        }
      } else {
        argValues = null;
      }
    }

    for (int i = 0; i < argCount; i++) {
      DfaValue arg = memState.pop();
      int paramIndex = argCount - i - 1;

      dropLocality(arg, memState);
      PsiElement anchor = instruction.getArgumentAnchor(paramIndex);
      Nullability requiredNullability = instruction.getArgRequiredNullability(paramIndex);
      if (requiredNullability == Nullability.NOT_NULL) {
        arg = dereference(memState, arg, NullabilityProblemKind.passingNullableToNotNullParameter.problem(anchor));
      }
      else if (requiredNullability == Nullability.UNKNOWN) {
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
    if (value instanceof DfaVariableValue && !(((DfaVariableValue)value).getVariableType() instanceof PsiArrayType)) {
      if (instruction.shouldFlushFields() || !(instruction.getResultType() instanceof PsiPrimitiveType)) {
        // For now drop locality on every qualified call except primitive returning pure calls
        // as value might escape through the return value
        dropLocality(value, memState);
      }
    }
    return value;
  }

  private Set<DfaCallState> addContractResults(MethodContract contract,
                                               Set<DfaCallState> states,
                                               DfaValueFactory factory,
                                               Set<DfaMemoryState> finalStates,
                                               DfaValue defaultResult,
                                               PsiExpression expression) {
    if(contract.isTrivial()) {
      for (DfaCallState callState : states) {
        DfaValue result = contract.getReturnValue().getDfaValue(factory, defaultResult, callState);
        pushExpressionResult(result, () -> expression, callState.myMemoryState);
        finalStates.add(callState.myMemoryState);
      }
      return Collections.emptySet();
    }

    Set<DfaCallState> falseStates = new LinkedHashSet<>();

    for (DfaCallState callState : states) {
      DfaMemoryState state = callState.myMemoryState;
      DfaCallArguments arguments = callState.myCallArguments;
      for (ContractValue contractValue : contract.getConditions()) {
        DfaValue condition = contractValue.makeDfaValue(factory, callState.myCallArguments);
        if (condition == null) {
          condition = DfaUnknownValue.getInstance();
        }
        DfaMemoryState falseState = state.createCopy();
        if (falseState.applyContractCondition(condition.createNegated())) {
          DfaCallArguments falseArguments = contractValue.updateArguments(arguments, true);
          falseStates.add(new DfaCallState(falseState, falseArguments));
        }
        if (!state.applyContractCondition(condition)) {
          state = null;
          break;
        }
        arguments = contractValue.updateArguments(arguments, false);
      }
      if(state != null) {
        DfaValue result = contract.getReturnValue().getDfaValue(factory, defaultResult, new DfaCallState(state, arguments));
        pushExpressionResult(result, () -> expression, state);
        finalStates.add(state);
      }
    }

    return falseStates;
  }

  private <T extends PsiElement> DfaValue dereference(DfaMemoryState memState,
                                                      DfaValue value,
                                                      @Nullable NullabilityProblemKind.NullabilityProblem<T> problem) {
    boolean ok = checkNotNullable(memState, value, problem);
    if (value instanceof DfaFactMapValue) {
      return ((DfaFactMapValue)value).withFact(DfaFactType.CAN_BE_NULL, false);
    }
    if (ok) return value;
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
      SpecialField field = SpecialField.findSpecialField(method);
      if (field != null) {
        return field.createValue(factory, qualifierValue);
      }
      DfaVariableSource source = DfaExpressionFactory.getAccessedVariableOrGetter(method);
      if (source != null) {
        return factory.getVarFactory().createVariableValue(source, instruction.getResultType(), (DfaVariableValue)qualifierValue);
      }
    }

    if (methodType == MethodCallInstruction.MethodType.UNBOXING) {
      return factory.getBoxedFactory().createUnboxed(qualifierValue);
    }

    if (methodType == MethodCallInstruction.MethodType.BOXING) {
      DfaValue boxed = factory.getBoxedFactory().createBoxed(qualifierValue);
      return boxed == null ? factory.createTypeValue(type, Nullability.NOT_NULL) : boxed;
    }

    if (methodType == MethodCallInstruction.MethodType.CAST) {
      assert qualifierValue != null;
      if (qualifierValue instanceof DfaConstValue && type != null) {
        Object casted = TypeConversionUtil.computeCastTo(((DfaConstValue)qualifierValue).getValue(), type);
        return factory.getConstFactory().createFromValue(casted, type, ((DfaConstValue)qualifierValue).getConstant());
      }
      return qualifierValue;
    }

    if (type != null && !(type instanceof PsiPrimitiveType)) {
      Nullability nullability = instruction.getReturnNullability();
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
        if (nullability == Nullability.UNKNOWN) {
          nullability = factory.suggestNullabilityForNonAnnotatedMember(targetMethod);
        }
      }
      DfaValue value = factory.createTypeValue(type, nullability);
      if (!instruction.shouldFlushFields() && instruction.getContext() instanceof PsiNewExpression) {
        value = factory.withFact(value, DfaFactType.LOCALITY, true);
      }
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
    if (notNullable && 
        !NullabilityProblemKind.passingNullableArgumentToNonAnnotatedParameter.isMyProblem(problem) &&
        !NullabilityProblemKind.assigningNullableValueToNonAnnotatedField.isMyProblem(problem)) {
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
    DfaValue result = DfaUnknownValue.getInstance();
    PsiType type = instruction.getResultType();
    if (PsiType.INT.equals(type) || PsiType.LONG.equals(type)) {
      LongRangeSet left = memState.getValueFact(dfaLeft, DfaFactType.RANGE);
      LongRangeSet right = memState.getValueFact(dfaRight, DfaFactType.RANGE);
      if (left != null && right != null) {
        LongRangeSet resultRange = left.binOpFromToken(opSign, right, PsiType.LONG.equals(type));
        if (resultRange != null) {
          result = runner.getFactory().getFactValue(DfaFactType.RANGE, resultRange);
        }
      }
    }
    if (result == DfaUnknownValue.getInstance() && JavaTokenType.PLUS == opSign && TypeUtils.isJavaLangString(type)) {
      result = runner.getFactory().createTypeValue(type, Nullability.NOT_NULL);
    }
    pushExpressionResult(result, instruction, memState);

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
    RelationType[] relations = splitRelation(relationType);

    ArrayList<DfaInstructionState> states = new ArrayList<>(relations.length);

    for (int i = 0; i < relations.length; i++) {
      RelationType relation = relations[i];
      DfaValue condition = factory.createCondition(dfaLeft, relation, dfaRight);
      if (condition instanceof DfaUnknownValue) return null;
      if (condition instanceof DfaConstValue) {
        Object value = ((DfaConstValue)condition).getValue();
        if (Boolean.FALSE.equals(value)) continue;
        if (Boolean.TRUE.equals(value)) {
          return makeBooleanResultArray(instruction, runner, memState, relationType.isSubRelation(relation));
        }
      }
      final DfaMemoryState copy = i == relations.length - 1 && !states.isEmpty() ? memState : memState.createCopy();
      if (copy.applyCondition(condition)) {
        boolean isTrue = relationType.isSubRelation(relation);
        states.add(makeBooleanResult(instruction, runner, copy, ThreeState.fromBoolean(isTrue)));
      }
    }
    if (states.isEmpty()) {
      // Neither of relations could be applied: likely comparison with NaN; do not split the state in this case, just push false
      memState.push(factory.getConstFactory().getFalse());
      return nextInstruction(instruction, runner, memState);
    }

    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
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

  @Override
  public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    myReachable.add(instruction);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();
    DfaValueFactory factory = runner.getFactory();
    if (!memState.isNotNull(dfaLeft)) {
      myCanBeNullInInstanceof.add(instruction);
    }
    boolean unknownTargetType = false;
    DfaValue condition = null;
    if (instruction.isClassObjectCheck()) {
      DfaConstValue constant = dfaRight instanceof DfaConstValue ? (DfaConstValue)dfaRight :
                               dfaRight instanceof DfaVariableValue ? memState.getConstantValue((DfaVariableValue)dfaRight) :
                               null;
      PsiType type = constant == null ? null : ObjectUtils.tryCast(constant.getValue(), PsiType.class);
      if (type == null || type instanceof PsiPrimitiveType) {
        // Unknown/primitive class: just execute contract "null -> false"
        DfaConstValue aNull = factory.getConstFactory().getNull();
        condition = factory.createCondition(dfaLeft, RelationType.NE, aNull);
        unknownTargetType = true;
      } else {
        dfaRight = factory.createTypeValue(type, Nullability.NOT_NULL);
      }
    }
    if (condition == null) {
      condition = factory.createCondition(dfaLeft, RelationType.IS, dfaRight);
    }

    boolean useful;
    ArrayList<DfaInstructionState> states = new ArrayList<>(2);
    if (condition instanceof DfaUnknownValue) {
      if (dfaLeft instanceof DfaFactMapValue && dfaRight instanceof DfaFactMapValue) {
        DfaFactMapValue left = (DfaFactMapValue)dfaLeft;
        DfaFactMapValue right = (DfaFactMapValue)dfaRight;
        useful = !right.getFacts().with(DfaFactType.CAN_BE_NULL, null).isSuperStateOf(left.getFacts());
      } else {
        useful = true;
      }
      states.add(makeBooleanResult(instruction, runner, memState, ThreeState.UNSURE));
    }
    else {
      final DfaMemoryState trueState = memState.createCopy();
      useful = unknownTargetType;
      if (trueState.applyCondition(condition)) {
        states.add(makeBooleanResult(instruction, runner, trueState, unknownTargetType ? ThreeState.UNSURE : ThreeState.YES));
      }
      if (memState.applyCondition(condition.createNegated())) {
        if (unknownTargetType) {
          memState.markEphemeral();
        }
        states.add(makeBooleanResult(instruction, runner, memState, ThreeState.NO));
        useful |= !memState.isNull(dfaLeft);
      }
    }
    if (useful) {
      myUsefulInstanceofs.add(instruction);
    }
    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Nullable
  private DfaInstructionState[] handleConstantComparison(BinopInstruction instruction,
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
      boolean result = dfaLeft == dfaRight ^ negated;
      return makeBooleanResultArray(instruction, runner, memState, result);
    }

    return null;
  }

  @Nullable
  private DfaInstructionState[] checkComparingWithConstant(BinopInstruction instruction,
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

  private DfaInstructionState[] checkComparisonWithKnownValue(BinopInstruction instruction,
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
    return makeBooleanResultArray(instruction, runner, memState, result);
  }

  private static int compare(Number a, Number b) {
    long aLong = a.longValue();
    long bLong = b.longValue();
    if (aLong != bLong) return aLong > bLong ? 1 : -1;

    return Double.compare(a.doubleValue(), b.doubleValue());
  }

  private DfaInstructionState[] makeBooleanResultArray(BinopInstruction instruction,
                                                       DataFlowRunner runner,
                                                       DfaMemoryState memState,
                                                       boolean result) {
    return new DfaInstructionState[]{makeBooleanResult(instruction, runner, memState, ThreeState.fromBoolean(result))};
  }

  private DfaInstructionState makeBooleanResult(BinopInstruction instruction,
                                                DataFlowRunner runner,
                                                DfaMemoryState memState,
                                                @NotNull ThreeState result) {
    DfaValue value = result == ThreeState.UNSURE ? DfaUnknownValue.getInstance() : runner.getFactory().getBoolean(result.toBoolean());
    pushExpressionResult(value, instruction, memState);
    if (result != ThreeState.NO) {
      instruction.setTrueReachable();
    }
    if (result != ThreeState.YES) {
      instruction.setFalseReachable();
    }
    return new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), memState);
  }

  public boolean isInstanceofRedundant(InstanceofInstruction instruction) {
    return !myUsefulInstanceofs.contains(instruction) && !instruction.isConditionConst() && myReachable.contains(instruction);
  }

  public boolean canBeNull(InstanceofInstruction instruction) {
    return myCanBeNullInInstanceof.contains(instruction);
  }
}

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
      flushArrayOnUnknownAssignment(instruction, runner.getFactory(), dfaDest, memState);
      return nextInstruction(instruction, runner, memState);
    }
    if (!(dfaDest instanceof DfaVariableValue &&
          ((DfaVariableValue)dfaDest).getPsiVariable() instanceof PsiLocalVariable &&
          dfaSource instanceof DfaVariableValue &&
          ControlFlowAnalyzer.isTempVariable((DfaVariableValue)dfaSource))) {
      dropLocality(dfaSource, memState);
    }

    PsiExpression lValue = PsiUtil.skipParenthesizedExprDown(instruction.getLExpression());
    PsiExpression rValue = instruction.getRExpression();
    if (lValue instanceof PsiArrayAccessExpression) {
      checkArrayElementAssignability(runner, memState, dfaSource, lValue, rValue);
    }

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;

      PsiModifierListOwner psi = var.getPsiVariable();
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
        stateImpl.setVariableState(var, stateImpl.getVariableState(var).withFact(DfaFactType.NULLABILITY, DfaNullability.NULLABLE));
      }
    }

    pushExpressionResult(dfaDest, instruction, memState);
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
  public DfaInstructionState[] visitArrayAccess(ArrayAccessInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiArrayAccessExpression arrayExpression = instruction.getExpression();
    DfaValue index = memState.pop();
    DfaValue array = memState.pop();
    boolean alwaysOutOfBounds = false;
    DfaValueFactory factory = runner.getFactory();
    if (index != DfaUnknownValue.getInstance()) {
      DfaValue indexNonNegative = factory.createCondition(index, RelationType.GE, factory.getInt(0));
      if (!memState.applyCondition(indexNonNegative)) {
        alwaysOutOfBounds = true;
      }
      DfaValue dfaLength = SpecialField.ARRAY_LENGTH.createValue(factory, array);
      DfaValue indexLessThanLength = factory.createCondition(index, RelationType.LT, dfaLength);
      if (!memState.applyCondition(indexLessThanLength)) {
        alwaysOutOfBounds = true;
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
  public DfaInstructionState[] visitMethodReference(MethodReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiMethodReferenceExpression expression = instruction.getExpression();
    final DfaValue qualifier = memState.pop();
    dropLocality(qualifier, memState);
    handleMethodReference(qualifier, expression, runner, memState);
    pushExpressionResult(runner.getFactory().createTypeValue(expression.getFunctionalInterfaceType(), Nullability.NOT_NULL), instruction, memState);

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
    dereference(state, callArguments.myQualifier, NullabilityProblemKind.callMethodRefNPE.problem(methodRef, null));
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

    DfaValue value = memState.pop();
    if (type instanceof PsiPrimitiveType) {
      value = DfaUtil.boxUnbox(value, type);
    }
    pushExpressionResult(value, instruction, memState);

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesCCE(TypeCastInstruction instruction) {}

  protected void beforeMethodCall(@NotNull PsiExpression expression,
                                  @NotNull DfaCallArguments arguments,
                                  @NotNull DataFlowRunner runner,
                                  @NotNull DfaMemoryState memState) {

  }

  @Override
  public DfaInstructionState[] visitMethodCall(final MethodCallInstruction instruction, final DataFlowRunner runner, final DfaMemoryState memState) {
    DfaValueFactory factory = runner.getFactory();
    DfaCallArguments callArguments = popCall(instruction, factory, memState);

    if (callArguments.myArguments != null && instruction.getExpression() != null) {
      beforeMethodCall(instruction.getExpression(), callArguments, runner, memState);
    }

    Set<DfaMemoryState> finalStates = ContainerUtil.newLinkedHashSet();
    finalStates.addAll(handleKnownMethods(instruction, runner, memState, callArguments));

    if (finalStates.isEmpty()) {
      Set<DfaCallState> currentStates = Collections.singleton(new DfaCallState(memState, callArguments));
      DfaValue defaultResult = getMethodResultValue(instruction, callArguments.myQualifier, memState, factory);
      if (callArguments.myArguments != null) {
        for (MethodContract contract : instruction.getContracts()) {
          currentStates = addContractResults(contract, currentStates, factory, finalStates, defaultResult, instruction.getExpression());
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
  private List<DfaMemoryState> handleKnownMethods(MethodCallInstruction instruction,
                                                  DataFlowRunner runner,
                                                  DfaMemoryState memState,
                                                  DfaCallArguments callArguments) {
    if (callArguments.myArguments == null) return Collections.emptyList();
    PsiMethod method = instruction.getTargetMethod();
    if (method == null) return Collections.emptyList();
    CustomMethodHandlers.CustomMethodHandler handler = CustomMethodHandlers.find(method);
    if (handler == null) return Collections.emptyList();
    DfaValue result = handler.getMethodResult(callArguments, memState, runner.getFactory());
    if (result == null) return Collections.emptyList();

    pushExpressionResult(result, instruction, memState);
    return Collections.singletonList(memState);
  }

  @NotNull
  protected DfaCallArguments popCall(MethodCallInstruction instruction, DfaValueFactory factory, DfaMemoryState memState) {
    PsiMethod method = instruction.getTargetMethod();
    MutationSignature sig = MutationSignature.fromMethod(method);
    DfaValue[] argValues = popCallArguments(instruction, factory, memState, sig);
    final DfaValue qualifier = popQualifier(instruction, memState, sig);
    return new DfaCallArguments(qualifier, argValues, !instruction.shouldFlushFields());
  }

  @Nullable
  private DfaValue[] popCallArguments(MethodCallInstruction instruction,
                                      DfaValueFactory factory,
                                      DfaMemoryState memState,
                                      MutationSignature sig) {
    final int argCount = instruction.getArgCount();

    PsiMethod method = instruction.getTargetMethod();
    boolean varargCall = instruction.isVarArgCall();
    DfaValue[] argValues = null;
    if (method != null) {
      PsiParameterList paramList = method.getParameterList();
      int paramCount = paramList.getParametersCount();
      if (paramCount == argCount || method.isVarArgs() && argCount >= paramCount - 1) {
        argValues = new DfaValue[paramCount];
        if (varargCall) {
          argValues[paramCount - 1] = factory.createTypeValue(paramList.getParameters()[paramCount - 1].getType(), Nullability.NOT_NULL);
        }
      }
    }

    for (int i = 0; i < argCount; i++) {
      DfaValue arg = memState.pop();
      int paramIndex = argCount - i - 1;

      dropLocality(arg, memState);
      PsiElement anchor = instruction.getArgumentAnchor(paramIndex);
      if (instruction.getContext() instanceof PsiMethodReferenceExpression) {
        PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)instruction.getContext();
        Nullability nullability = instruction.getArgRequiredNullability(paramIndex);
        if (nullability == Nullability.NOT_NULL) {
          arg = dereference(memState, arg, NullabilityProblemKind.passingToNotNullMethodRefParameter.problem(methodRef, null));
        } else if (nullability == Nullability.UNKNOWN) {
          checkNotNullable(memState, arg, NullabilityProblemKind.passingToNonAnnotatedMethodRefParameter.problem(methodRef, null));
        }
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
    DfaValue value = memState.pop();
    if (instruction.getContext() instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression context = (PsiMethodReferenceExpression)instruction.getContext();
      value = dereference(memState, value, NullabilityProblemKind.callMethodRefNPE.problem(context, null));
    }
    if (sig.mutatesThis() && !memState.applyFact(value, DfaFactType.MUTABILITY, Mutability.MUTABLE)) {
      reportMutabilityViolation(true, instruction.getContext());
      if (value instanceof DfaVariableValue) {
        memState.forceVariableFact((DfaVariableValue)value, DfaFactType.MUTABILITY, Mutability.MUTABLE);
      }
    }
    if (value instanceof DfaVariableValue && !(value.getType() instanceof PsiArrayType)) {
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
        DfaValue falseCondition = condition.createNegated();
        if (contract.getReturnValue().isFail() ? 
            falseState.applyCondition(falseCondition) : 
            falseState.applyContractCondition(falseCondition)) {
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
      return ((DfaFactMapValue)value).withFact(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL);
    }
    if (ok) return value;
    if (memState.isNull(value) && problem != null && problem.getKind() == NullabilityProblemKind.nullableFunctionReturn) {
      return value.getFactory().getFactValue(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL);
    }
    if (value instanceof DfaVariableValue) {
      memState.forceVariableFact((DfaVariableValue)value, DfaFactType.NULLABILITY, DfaNullability.NOT_NULL);
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

    if (instruction.getContext() instanceof PsiMethodReferenceExpression && qualifierValue instanceof DfaVariableValue) {
      PsiMethod method = instruction.getTargetMethod();
      VariableDescriptor descriptor = DfaExpressionFactory.getAccessedVariableOrGetter(method);
      if (descriptor != null) {
        return descriptor.createValue(factory, qualifierValue, true);
      }
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
      DfaValue value = instruction.getContext() instanceof PsiNewExpression ?
                       factory.createExactTypeValue(type) :
                       factory.createTypeValue(type, nullability);
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
    if (notNullable && problem != null && problem.thrownException() != null) {
      DfaValueFactory factory = ((DfaMemoryStateImpl)state).getFactory();
      state.applyCondition(factory.createCondition(value, RelationType.NE, factory.getConstFactory().getNull()));
    }
    return notNullable;
  }

  @Override
  public DfaInstructionState[] visitConvertPrimitive(PrimitiveConversionInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState state) {
    DfaValue value = state.pop();
    DfaValue result = getConversionResult(value, instruction.getTargetType(), runner.getFactory(), state);
    pushExpressionResult(result, instruction, state);
    return nextInstruction(instruction, runner, state);
  }
  
  private static DfaValue getConversionResult(DfaValue value, PsiPrimitiveType type, DfaValueFactory factory, DfaMemoryState state) {
    if (value instanceof DfaVariableValue && TypeConversionUtil.isSafeConversion(type, value.getType())) {
      return value;
    }
    DfaConstValue constValue = state.getConstantValue(value);
    if (constValue != null && type != null) {
      Object casted = TypeConversionUtil.computeCastTo(constValue.getValue(), type);
      return factory.getConstFactory().createFromValue(casted, type);
    }
    if (TypeConversionUtil.isIntegralNumberType(type)) {
      LongRangeSet range = state.getValueFact(value, DfaFactType.RANGE);
      if (range == null) range = LongRangeSet.all();
      return factory.getFactValue(DfaFactType.RANGE, range.castTo(type));
    }
    return DfaUnknownValue.getInstance();
  }

  @Override
  public DfaInstructionState[] visitCheckNotNull(CheckNotNullInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    NullabilityProblemKind.NullabilityProblem<?> problem = instruction.getProblem();
    if (problem.thrownException() == null) {
      checkNotNullable(memState, memState.peek(), problem);
    } else {
      DfaControlTransferValue transfer = instruction.getOnNullTransfer();
      if (transfer == null) {
        memState.push(dereference(memState, memState.pop(), problem));
      } else {
        DfaValue value = memState.pop();
        List<DfaInstructionState> result = new ArrayList<>();
        DfaMemoryState nullState = memState.createCopy();
        memState.push(dereference(memState, value, problem));
        result.add(new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), memState));
        DfaValueFactory factory = runner.getFactory();
        if (nullState.applyCondition(factory.createCondition(value, RelationType.EQ, factory.getConstFactory().getNull()))) {
          List<DfaInstructionState> dispatched = transfer.dispatch(nullState, runner);
          for (DfaInstructionState npeState : dispatched) {
            npeState.getMemoryState().markEphemeral();
          }
          result.addAll(dispatched);
        }
        return result.toArray(DfaInstructionState.EMPTY_ARRAY);
      }
    }
    return super.visitCheckNotNull(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitNot(NotInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaValue = memState.pop();

    DfaMemoryState falseState = memState.createCopy();
    DfaValueFactory factory = runner.getFactory();
    List<DfaInstructionState> result = new ArrayList<>(2);
    if (memState.applyCondition(dfaValue.createNegated())) {
      pushExpressionResult(factory.getBoolean(true), instruction, memState);
      result.add(new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), memState));
    }
    if (falseState.applyCondition(dfaValue)) {
      pushExpressionResult(factory.getBoolean(false), instruction, falseState);
      result.add(new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), falseState));
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final IElementType opSign = instruction.getOperationSign();
    RelationType relationType = RelationType.fromElementType(opSign);
    if (relationType != null) {
      DfaInstructionState[] states = handleRelationBinop(instruction, runner, memState, dfaRight, dfaLeft, relationType);
      if (states != null) {
        return states;
      }
    }
    DfaValue result = DfaUnknownValue.getInstance();
    PsiType type = instruction.getResultType();
    if (PsiType.INT.equals(type) || PsiType.LONG.equals(type)) {
      boolean isLong = PsiType.LONG.equals(type);
      result = runner.getFactory().getBinOpFactory().create(dfaLeft, dfaRight, memState, isLong, opSign);
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
    if((relationType == RelationType.EQ || relationType == RelationType.NE) &&
       (dfaLeft != dfaRight || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaConstValue) && 
       isComparedByEquals(instruction.getExpression()) && !memState.isNull(dfaLeft) && !memState.isNull(dfaRight)) {
      ArrayList<DfaInstructionState> states = new ArrayList<>(2);
      DfaMemoryState equality = memState.createCopy();
      if (equality.applyCondition(factory.createCondition(dfaLeft, RelationType.EQ, dfaRight))) {
        states.add(makeBooleanResult(instruction, runner, equality, ThreeState.UNSURE));
      }
      if (memState.applyCondition(factory.createCondition(dfaLeft, RelationType.NE, dfaRight))) {
        states.add(makeBooleanResult(instruction, runner, memState, ThreeState.fromBoolean(relationType == RelationType.NE)));
      }
      return states.toArray(DfaInstructionState.EMPTY_ARRAY);
    }
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
          DfaInstructionState state =
            makeBooleanResult(instruction, runner, memState, ThreeState.fromBoolean(relationType.isSubRelation(relation)));
          return new DfaInstructionState[]{state};
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

  private static boolean isComparedByEquals(PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression) {
      PsiExpression left = ((PsiBinaryExpression)expression).getLOperand();
      PsiExpression right = ((PsiBinaryExpression)expression).getROperand();
      return right != null && (DfaUtil.isComparedByEquals(left.getType()) && DfaUtil.isComparedByEquals(right.getType()));
    }
    return false;
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
      DfaConstValue constant = memState.getConstantValue(dfaRight);
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
        useful = !right.getFacts().with(DfaFactType.NULLABILITY, null).isSuperStateOf(left.getFacts());
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

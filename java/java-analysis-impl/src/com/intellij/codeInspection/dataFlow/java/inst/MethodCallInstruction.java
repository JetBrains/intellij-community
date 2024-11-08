// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.problems.MutabilityProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfStreamStateType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.dataFlow.jvm.SpecialField.CONSUMED_STREAM;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM;
import static com.intellij.util.ObjectUtils.tryCast;


public class MethodCallInstruction extends ExpressionPushingInstruction {
  private static final Logger LOG = Logger.getInstance(MethodCallInstruction.class);
  private static final Nullability[] EMPTY_NULLABILITY_ARRAY = new Nullability[0];

  private final @Nullable PsiType myType;
  private final int myArgCount;
  private final @NotNull MutationSignature myMutation;
  private final @NotNull PsiElement myContext; // PsiCall or PsiMethodReferenceExpression
  private final @Nullable PsiMethod myTargetMethod;
  private final List<MethodContract> myContracts;
  private final @Nullable DfaValue myPrecalculatedReturnValue;
  private final Nullability[] myArgRequiredNullability;
  private final Nullability myReturnNullability;

  public MethodCallInstruction(@NotNull PsiMethodReferenceExpression reference, @NotNull List<? extends MethodContract> contracts) {
    super(new JavaMethodReferenceReturnAnchor(reference));
    myContext = reference;
    JavaResolveResult resolveResult = reference.advancedResolve(false);
    myTargetMethod = tryCast(resolveResult.getElement(), PsiMethod.class);
    myContracts = Collections.unmodifiableList(contracts);
    myArgCount = myTargetMethod == null ? 0 : myTargetMethod.getParameterList().getParametersCount();
    if (myTargetMethod == null) {
      myType = null;
      myReturnNullability = Nullability.UNKNOWN;
    }
    else {
      if (myTargetMethod.isConstructor()) {
        PsiClass containingClass = myTargetMethod.getContainingClass();
        myType = containingClass == null ? null : JavaPsiFacade.getElementFactory(myTargetMethod.getProject())
          .createType(containingClass, resolveResult.getSubstitutor());
        myReturnNullability = Nullability.NOT_NULL;
      }
      else {
        myType = resolveResult.getSubstitutor().substitute(myTargetMethod.getReturnType());
        myReturnNullability = DfaPsiUtil.getElementNullability(myType, myTargetMethod);
      }
    }
    myPrecalculatedReturnValue = null;
    myArgRequiredNullability = myTargetMethod == null
                               ? EMPTY_NULLABILITY_ARRAY
                               : calcArgRequiredNullability(resolveResult.getSubstitutor(),
                                                            myTargetMethod.getParameterList().getParameters());
    myMutation = MutationSignature.fromMethod(myTargetMethod);
  }

  public MethodCallInstruction(@NotNull PsiCall call, @Nullable DfaValue precalculatedReturnValue, List<? extends MethodContract> contracts) {
    super(call instanceof PsiExpression expr ? new JavaExpressionAnchor(expr) : null);
    myContext = call;
    myContracts = Collections.unmodifiableList(contracts);
    final PsiExpressionList argList = call.getArgumentList();
    PsiExpression[] args = argList != null ? argList.getExpressions() : PsiExpression.EMPTY_ARRAY;
    myType = call instanceof PsiExpression expr ? expr.getType() : null;

    JavaResolveResult result = call.resolveMethodGenerics();
    myTargetMethod = (PsiMethod)result.getElement();
    myArgCount = myTargetMethod != null && MethodCallUtils.isVarArgCall(call) ? myTargetMethod.getParameterList().getParametersCount() : args.length;

    PsiSubstitutor substitutor = result.getSubstitutor();
    if (argList != null && myTargetMethod != null) {
      PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
      myArgRequiredNullability = calcArgRequiredNullability(substitutor, parameters);
    } else {
      myArgRequiredNullability = EMPTY_NULLABILITY_ARRAY;
    }

    myMutation = MutationSignature.fromCall(call);
    myPrecalculatedReturnValue = DfaTypeValue.isUnknown(precalculatedReturnValue) ? null : precalculatedReturnValue;
    myReturnNullability = call instanceof PsiNewExpression ? Nullability.NOT_NULL : DfaPsiUtil.getElementNullability(myType, myTargetMethod);
  }

  private MethodCallInstruction(@NotNull MethodCallInstruction from, @NotNull DfaValue precalculatedReturnValue) {
    super(from.getDfaAnchor());
    myPrecalculatedReturnValue = precalculatedReturnValue;
    myContext = from.myContext;
    myContracts = from.myContracts;
    myArgCount = from.myArgCount;
    myMutation = from.myMutation;
    myType = from.myType;
    myTargetMethod = from.myTargetMethod;
    myArgRequiredNullability = from.myArgRequiredNullability;
    myReturnNullability = from.myReturnNullability;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myPrecalculatedReturnValue == null) return this;
    return new MethodCallInstruction(this, myPrecalculatedReturnValue.bindToFactory(factory));
  }

  /**
   * Returns a PsiElement which at best represents an argument with given index
   *
   * @param index an argument index, must be from 0 to {@link #getArgCount()}-1.
   * @return a PsiElement. Either argument expression or method reference if call is described by method reference
   */
  public PsiElement getArgumentAnchor(int index) {
    if (myContext instanceof PsiCall) {
      PsiExpressionList argumentList = ((PsiCall)myContext).getArgumentList();
      if (argumentList != null) {
        return argumentList.getExpressions()[index];
      }
    }
    if (myContext instanceof PsiMethodReferenceExpression) {
      return ((PsiMethodReferenceExpression)myContext).getReferenceNameElement();
    }
    throw new AssertionError();
  }

  private Nullability[] calcArgRequiredNullability(PsiSubstitutor substitutor, PsiParameter[] parameters) {
    if (myArgCount == 0) {
      return EMPTY_NULLABILITY_ARRAY;
    }

    int checkedCount = Math.min(myArgCount, parameters.length);

    Nullability[] nullabilities = new Nullability[myArgCount];
    for (int i = 0; i < checkedCount; i++) {
      nullabilities[i] = DfaPsiUtil.getElementNullability(substitutor.substitute(parameters[i].getType()), parameters[i]);
    }
    return nullabilities;
  }

  public static boolean isVarArgCall(PsiMethod method, PsiSubstitutor substitutor, PsiExpression[] args, PsiParameter[] parameters) {
    if (!method.isVarArgs()) {
      return false;
    }

    int argCount = args.length;
    int paramCount = parameters.length;
    if (argCount > paramCount || argCount == paramCount - 1) {
      return true;
    }

    if (paramCount > 0 && argCount == paramCount) {
      PsiType lastArgType = args[argCount - 1].getType();
      return lastArgType != null && !substitutor.substitute(parameters[paramCount - 1].getType()).isAssignableFrom(lastArgType);
    }
    return false;
  }

  public @Nullable PsiType getResultType() {
    return myType;
  }

  public int getArgCount() {
    return myArgCount;
  }

  public @NotNull MutationSignature getMutationSignature() {
    return myMutation;
  }

  public @Nullable PsiMethod getTargetMethod() {
    return myTargetMethod;
  }

  public @Nullable Nullability getArgRequiredNullability(int index) {
    return index >= myArgRequiredNullability.length ? null : myArgRequiredNullability[index];
  }

  public List<MethodContract> getContracts() {
    return myContracts;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValueFactory factory = interpreter.getFactory();
    DfaCallArguments callArguments = this.popCall(interpreter, stateBefore);

    Set<DfaMemoryState> finalStates = new LinkedHashSet<>();

    PsiType qualifierType = DfaPsiUtil.dfTypeToPsiType(factory.getProject(), stateBefore.getDfType(callArguments.getQualifier()));
    PsiMethod realMethod = findSpecificMethod(qualifierType);
    DfaValue defaultResult = getMethodResultValue(callArguments, stateBefore, factory, realMethod);
    DfaCallState initialState = new DfaCallState(stateBefore, callArguments, defaultResult);
    Set<DfaCallState> currentStates = Collections.singleton(initialState);
    if (callArguments.getArguments() != null && !(defaultResult.getDfType() instanceof DfConstantType)) {
      for (MethodContract contract : getContracts()) {
        currentStates = addContractResults(contract, currentStates, factory, finalStates);
        if (currentStates.size() + finalStates.size() > interpreter.getComplexityLimit()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Too complex contract on " + getContext() + ", skipping contract processing");
          }
          finalStates.clear();
          currentStates = Collections.singleton(initialState);
          break;
        }
      }
    }
    for (DfaCallState callState : currentStates) {
      callState.getMemoryState().push(defaultResult);
      finalStates.add(callState.getMemoryState());
    }

    DfaInstructionState[] result = new DfaInstructionState[finalStates.size()];
    int i = 0;
    DfaValue[] args = callArguments.toArray();
    for (DfaMemoryState state : finalStates) {
      ContractValue.flushContractTempVariables(state);
      boolean keepNonFlushed = state.peek() instanceof DfaVariableValue;
      DfaValue tos = null;
      if (keepNonFlushed) {
        tos = state.pop();
      }
      callArguments.flush(state, factory, realMethod);
      if (!keepNonFlushed) {
        tos = state.pop();
      }
      pushResult(interpreter, state, tos, args);
      result[i++] = nextState(interpreter, state);
    }
    return result;
  }

  public @Nullable PsiCall getCallExpression() {
    return tryCast(myContext, PsiCall.class);
  }

  public @NotNull PsiElement getContext() {
    return myContext;
  }

  public String toString() {
    if (myContext instanceof PsiCall) {
      return "CALL_METHOD: " + myContext.getText();
    } else {
      return "CALL_METHOD_REFERENCE: " + myContext.getText();
    }
  }

  static Set<DfaCallState> addContractResults(@NotNull MethodContract contract,
                                              @NotNull Set<DfaCallState> states,
                                              @NotNull DfaValueFactory factory,
                                              @NotNull Set<DfaMemoryState> finalStates) {
    if (contract.isTrivial()) {
      for (DfaCallState callState : states) {
        DfaValue result = contract.getReturnValue().getDfaValue(factory, callState);
        callState.getMemoryState().push(result);
        finalStates.add(callState.getMemoryState());
      }
      return Collections.emptySet();
    }

    Set<DfaCallState> falseStates = new LinkedHashSet<>();

    for (DfaCallState callState : states) {
      for (ContractValue condition : contract.getConditions()) {
        callState = condition.updateState(callState);
      }
      DfaMemoryState state = callState.getMemoryState();
      DfaCallArguments arguments = callState.getCallArguments();
      for (ContractValue contractValue : contract.getConditions()) {
        DfaCondition condition = contractValue.makeCondition(factory, callState.getCallArguments());
        DfaMemoryState falseState = state.createCopy();
        DfaCondition falseCondition = condition.negate();
        if (contract.getReturnValue().isFail() ?
            falseState.applyCondition(falseCondition) :
            falseState.applyContractCondition(falseCondition)) {
          falseStates.add(callState.withMemoryState(falseState).withArguments(arguments));
        }
        if (!state.applyContractCondition(condition)) {
          state = null;
          break;
        }
      }
      if (state != null) {
        DfaValue result = contract.getReturnValue().getDfaValue(factory, callState.withArguments(arguments));
        state.push(result);
        finalStates.add(state);
      }
    }

    return falseStates;
  }

  private PsiMethod findSpecificMethod(@Nullable PsiType qualifierType) {
    if (myTargetMethod == null || qualifierType == null || !PsiUtil.canBeOverridden(myTargetMethod)) return myTargetMethod;
    PsiExpression qualifierExpression = null;
    if (myContext instanceof PsiMethodCallExpression) {
      qualifierExpression = ((PsiMethodCallExpression)myContext).getMethodExpression().getQualifierExpression();
    }
    else if (myContext instanceof PsiMethodReferenceExpression) {
      qualifierExpression = ((PsiMethodReferenceExpression)myContext).getQualifierExpression();
    }
    if (qualifierExpression instanceof PsiSuperExpression) return myTargetMethod; // non-virtual call
    return MethodUtils.findSpecificMethod(myTargetMethod, qualifierType);
  }

  private static @NotNull PsiType narrowReturnType(@NotNull PsiType returnType, @Nullable PsiType qualifierType,
                                                   @NotNull PsiMethod realMethod) {
    PsiClass containingClass = realMethod.getContainingClass();
    PsiType realReturnType = realMethod.getReturnType();
    if (containingClass != null && qualifierType instanceof PsiClassType) {
      if (containingClass.hasTypeParameters() || containingClass.getContainingClass() != null) {
        PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)qualifierType).resolveGenerics();
        PsiClass subType = classResolveResult.getElement();
        if (subType != null && !subType.equals(containingClass)) {
          PsiSubstitutor substitutor = TypeConversionUtil
            .getMaybeSuperClassSubstitutor(containingClass, subType, classResolveResult.getSubstitutor());
          if (substitutor != null) {
            realReturnType = substitutor.substitute(realReturnType);
          }
        }
      }
    }
    if (realReturnType != null && !realReturnType.equals(returnType) &&
        TypeConversionUtil.erasure(returnType).isAssignableFrom(realReturnType)) {
      // possibly covariant return type
      return realReturnType;
    }
    return returnType;
  }

  private @NotNull DfaValue getMethodResultValue(@NotNull DfaCallArguments callArguments,
                                                 @NotNull DfaMemoryState state, 
                                                 @NotNull DfaValueFactory factory, 
                                                 PsiMethod realMethod) {
    if (callArguments.getArguments() != null && myTargetMethod != null) {
      CustomMethodHandlers.CustomMethodHandler handler = CustomMethodHandlers.find(myTargetMethod);
      if (handler != null) {
        DfaValue value = handler.getMethodResultValue(callArguments, state, factory, myTargetMethod);
        if (value != null) {
          if (myPrecalculatedReturnValue != null) {
            if (!state.applyCondition(myPrecalculatedReturnValue.eq(value))) {
              throw new IllegalStateException("Precalculated value " +
                                              myPrecalculatedReturnValue +
                                              " mismatches with method handler result " +
                                              value +
                                              "; method = " +
                                              PsiFormatUtil.formatMethod(myTargetMethod, PsiSubstitutor.EMPTY,
                                                                         PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                         PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_TYPE));
            }
          }
          return myPrecalculatedReturnValue instanceof DfaVariableValue var && !var.isFlushableByCalls()
                 ? myPrecalculatedReturnValue
                 : value;
        }
      }
    }
    DfaValue qualifierValue = callArguments.getQualifier();
    PsiType type = getResultType();

    VariableDescriptor descriptor = JavaDfaValueFactory.getAccessedVariableOrGetter(realMethod);
    if (descriptor instanceof SpecialField || descriptor != null && qualifierValue instanceof DfaVariableValue) {
      return descriptor.createValue(factory, qualifierValue);
    }
    if (myPrecalculatedReturnValue != null) {
      return myPrecalculatedReturnValue;
    }

    if (type != null && !(type instanceof PsiPrimitiveType)) {
      Nullability nullability = myReturnNullability;
      Mutability mutable = Mutability.UNKNOWN;
      if (myTargetMethod != null) {
        if (realMethod != myTargetMethod) {
          nullability = DfaPsiUtil.getElementNullability(type, realMethod);
          mutable = Mutability.getMutability(realMethod);
        } else {
          mutable = Mutability.getMutability(myTargetMethod);
        }
        if (type.hasAnnotation(Mutability.UNMODIFIABLE_ANNOTATION)) {
          mutable = Mutability.UNMODIFIABLE;
        }
        else if (type.hasAnnotation(Mutability.UNMODIFIABLE_VIEW_ANNOTATION)) {
          mutable = Mutability.UNMODIFIABLE_VIEW;
        }
        PsiType qualifierType = DfaPsiUtil.dfTypeToPsiType(factory.getProject(), state.getDfType(qualifierValue));
        type = narrowReturnType(type, qualifierType, realMethod);
      }
      DfType dfType = getContext() instanceof PsiNewExpression ?
                      TypeConstraints.exact(type).asDfType().meet(NOT_NULL_OBJECT) :
                      TypeConstraints.instanceOf(type).asDfType().meet(DfaNullability.fromNullability(nullability).asDfType());
      if (myMutation.isPure() && getContext() instanceof PsiNewExpression &&
          !TypeConstraint.fromDfType(dfType).isComparedByEquals()) {
        dfType = dfType.meet(LOCAL_OBJECT);
      }
      if (InheritanceUtil.isInheritor(type, JAVA_UTIL_STREAM_BASE_STREAM)) {
        dfType = dfType.meet(((DfReferenceType)CONSUMED_STREAM.asDfType(DfStreamStateType.OPEN)).dropNullability());
        if (myReturnNullability == Nullability.NOT_NULL) {
          dfType = dfType.meet(LOCAL_OBJECT);
        }
      }

      return factory.fromDfType(dfType.meet(mutable.asDfType()));
    }
    LongRangeSet range = JvmPsiRangeSetUtil.typeRange(type, true);
    if (range != null) {
      if (myTargetMethod != null) {
        range = range.meet(JvmPsiRangeSetUtil.fromPsiElement(myTargetMethod));
      }
      return factory.fromDfType(PsiTypes.longType().equals(type) ? longRange(range) : intRangeClamped(range));
    }
    return PsiTypes.voidType().equals(type) ? factory.getUnknown() : factory.fromDfType(typedObject(type, Nullability.UNKNOWN));
  }

  private boolean mayLeakThis(@NotNull DfaMemoryState memState, DfaValue @Nullable [] argValues) {
    MutationSignature signature = getMutationSignature();
    if (signature == MutationSignature.unknown()) return true;
    if (JavaDfaHelpers.mayLeakFromType(typedObject(getResultType(), Nullability.UNKNOWN))) return true;
    if (argValues == null) {
      return signature.isPure() || signature.equals(MutationSignature.pure().alsoMutatesThis());
    }
    for (int i = 0; i < argValues.length; i++) {
      if (signature.mutatesArg(i)) {
        DfType type = memState.getDfType(argValues[i]);
        if (JavaDfaHelpers.mayLeakFromType(type)) return true;
      }
    }
    return false;
  }

  private DfaValue popQualifier(@NotNull DataFlowInterpreter interpreter,
                                @NotNull DfaMemoryState memState,
                                DfaValue @Nullable [] argValues) {
    DfaValue value = memState.pop();
    if (getContext() instanceof PsiMethodReferenceExpression context && MethodReferenceInstruction.isQualifierDereferenced(context)) {
      value = CheckNotNullInstruction.dereference(
        interpreter, memState, value, NullabilityProblemKind.callMethodRefNPE.problem(context, null));
    }
    DfType dfType = memState.getDfType(value);
    if (getMutationSignature().mutatesThis() && !Mutability.fromDfType(dfType).canBeModified()) {
      PsiMethod method = getTargetMethod();
      // Inferred mutation annotation may infer mutates="this" if invisible state is mutated (e.g. cached hashCode is stored).
      // So let's conservatively skip the warning here. Such contract is still useful because it assures that nothing else is mutated.
      if (method != null && JavaMethodContractUtil.hasExplicitContractAnnotation(method)) {
        interpreter.getListener().onCondition(new MutabilityProblem(getContext(), true), value, ThreeState.YES, memState);
        memState.updateDfType(
          value, type -> type instanceof DfReferenceType refType ? refType.dropMutability().meet(Mutability.MUTABLE.asDfType()) : type);
      }
    }
    TypeConstraint constraint = TypeConstraint.fromDfType(dfType);
    if (!HardcodedContracts.isKnownNoQualifierLeak(getTargetMethod()) &&
        !constraint.isArray() && (constraint.isComparedByEquals() || mayLeakThis(memState, argValues))) {
      value = JavaDfaHelpers.dropLocality(value, memState);
    }
    return value;
  }

  private DfaValue @Nullable [] popCallArguments(DataFlowInterpreter interpreter, DfaMemoryState memState) {
    DfaValue[] argValues = null;
    PsiParameterList paramList = null;
    if (myTargetMethod != null) {
      paramList = myTargetMethod.getParameterList();
      int paramCount = paramList.getParametersCount();
      if (paramCount == myArgCount) {
        argValues = paramCount == 0 ? DfaValue.EMPTY_ARRAY : new DfaValue[paramCount];
      }
    }

    for (int i = 0; i < myArgCount; i++) {
      DfaValue arg = memState.pop();
      int paramIndex = myArgCount - i - 1;

      boolean parameterMayNotLeak =
        HardcodedContracts.isKnownNoParameterLeak(myTargetMethod) ||
        (myMutation.isPure() ||
         myMutation.equals(MutationSignature.pure().alsoMutatesArg(paramIndex))) &&
        !JavaDfaHelpers.mayLeakFromType(typedObject(getResultType(), Nullability.UNKNOWN));
      if (!parameterMayNotLeak) {
        // If we write to local object only, it should not leak
        arg = JavaDfaHelpers.dropLocality(arg, memState);
      }
      if (getContext() instanceof PsiMethodReferenceExpression methodRef && paramList != null) {
        PsiParameter parameter = paramList.getParameter(paramIndex);
        if (parameter != null) {
          Nullability nullability = getArgRequiredNullability(paramIndex);
          arg = MethodReferenceInstruction.adaptMethodRefArgument(interpreter, memState, arg, methodRef, parameter, nullability);
        }
      }
      if (myMutation.mutatesArg(paramIndex)) {
        DfType dfType = memState.getDfType(arg);
        if (!Mutability.fromDfType(dfType).canBeModified() &&
            // Empty array cannot be modified at all
            !memState.getDfType(SpecialField.ARRAY_LENGTH.createValue(interpreter.getFactory(), arg)).equals(intValue(0))) {
          PsiElement anchor = getArgumentAnchor(paramIndex);
          interpreter.getListener().onCondition(new MutabilityProblem(anchor, false), arg, ThreeState.YES, memState);
          memState.updateDfType(
            arg, type -> type instanceof DfReferenceType refType ? refType.dropMutability().meet(Mutability.MUTABLE.asDfType()) : type);
        }
      }
      if (argValues != null) {
        argValues[paramIndex] = arg;
      }
    }
    return argValues;
  }

  private @NotNull DfaCallArguments popCall(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState memState) {
    DfaValue[] argValues = popCallArguments(interpreter, memState);
    final DfaValue qualifier = popQualifier(interpreter, memState, argValues);
    return new DfaCallArguments(qualifier, argValues, getMutationSignature());
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return myPrecalculatedReturnValue instanceof DfaVariableValue var ? 
           List.of(var.getDescriptor()) : List.of();
  }
}

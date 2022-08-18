// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.typedObject;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * This instruction pops a top-of-stack value dereferencing it (so nullability warning might be issued if top-of-stack is nullable value).
 */
public class MethodReferenceInstruction extends ExpressionPushingInstruction {
  public MethodReferenceInstruction(@NotNull PsiMethodReferenceExpression expression) {
    super(new JavaExpressionAnchor(expression));
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    PsiMethodReferenceExpression expression = getMethodReference();
    DfaValue qualifier = stateBefore.pop();
    JavaDfaHelpers.dropLocality(qualifier, stateBefore);
    handleMethodReference(qualifier, interpreter, stateBefore);
    pushResult(interpreter, stateBefore, JavaDfaHelpers.getFunctionDfType(expression));
    return nextStates(interpreter, stateBefore);
  }

  public String toString() {
    return "METHOD_REF: " + getDfaAnchor();
  }
  
  public @NotNull PsiMethodReferenceExpression getMethodReference() {
    return ((PsiMethodReferenceExpression)((JavaExpressionAnchor)Objects.requireNonNull(getDfaAnchor())).getExpression());
  }

  private void handleMethodReference(@NotNull DfaValue qualifier, @NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState state) {
    PsiMethodReferenceExpression methodRef = getMethodReference();
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(methodRef);
    if (sam == null) return;
    JavaResolveResult resolveResult = methodRef.advancedResolve(false);
    PsiMethod method = tryCast(resolveResult.getElement(), PsiMethod.class);
    if (method == null) return;
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    DfaCallArguments callArguments = getMethodReferenceCallArguments(state, methodRef, qualifier, interpreter, sam, method, substitutor);
    if (isQualifierDereferenced(methodRef)) {
      CheckNotNullInstruction.dereference(interpreter, state, callArguments.getQualifier(),
                                          NullabilityProblemKind.callMethodRefNPE.problem(methodRef, null));
    }
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
    if (contracts.isEmpty() || !JavaMethodContractUtil.isPure(method)) return;
    PsiType returnType = substitutor.substitute(method.getReturnType());
    DfaValue defaultResult = interpreter.getFactory().fromDfType(typedObject(returnType, DfaPsiUtil.getElementNullability(returnType, method)));
    Set<DfaCallState> currentStates = Collections.singleton(new DfaCallState(state.createClosureState(), callArguments, defaultResult));
    JavaMethodReferenceReturnAnchor anchor = new JavaMethodReferenceReturnAnchor(methodRef);
    DfaValue[] args = callArguments.toArray();
    for (MethodContract contract : contracts) {
      Set<DfaMemoryState> results = new HashSet<>();
      currentStates = MethodCallInstruction.addContractResults(contract, currentStates, interpreter.getFactory(), results);
      for (DfaMemoryState result : results) {
        ContractValue.flushContractTempVariables(result);
        DfaValue value = result.pop();
        interpreter.getListener().beforePush(args, value, anchor, result);
        result.push(value);
      }
    }
    for (DfaCallState currentState: currentStates) {
      interpreter.getListener().beforePush(args, defaultResult, anchor, currentState.getMemoryState());
      currentState.getMemoryState().push(defaultResult);
      ContractValue.flushContractTempVariables(currentState.getMemoryState());
    }
  }

  static boolean isQualifierDereferenced(@NotNull PsiMethodReferenceExpression methodRef) {
    if (methodRef.isConstructor()) return false;
    PsiElement target = methodRef.resolve();
    if (!(target instanceof PsiMethod)) return false;
    if (((PsiMethod)target).hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!PsiMethodReferenceUtil.isStaticallyReferenced(methodRef)) return false;
    return true;
  }

  private static @NotNull DfaCallArguments getMethodReferenceCallArguments(@NotNull DfaMemoryState state,
                                                                           @NotNull PsiMethodReferenceExpression methodRef,
                                                                           DfaValue qualifier,
                                                                           @NotNull DataFlowInterpreter interpreter,
                                                                           @NotNull PsiMethod sam,
                                                                           @NotNull PsiMethod method,
                                                                           @NotNull PsiSubstitutor substitutor) {
    PsiParameter[] samParameters = sam.getParameterList().getParameters();
    boolean firstParameterIsQualifier = isQualifierDereferenced(methodRef);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    DfaValue[] arguments = new DfaValue[parameters.length];
    Arrays.fill(arguments, interpreter.getFactory().getUnknown());
    for (int i = 0; i < samParameters.length; i++) {
      DfaValue value = interpreter.getFactory().fromDfType(
        typedObject(substitutor.substitute(samParameters[i].getType()), DfaPsiUtil.getFunctionalParameterNullability(methodRef, i)));
      if (i == 0 && firstParameterIsQualifier) {
        qualifier = value;
      }
      else {
        int idx = i - (firstParameterIsQualifier ? 1 : 0);
        if (idx >= arguments.length) break;
        PsiType parameterType = parameters[idx].getType();
        if (!(parameterType instanceof PsiEllipsisType)) {
          Nullability nullability = DfaPsiUtil.getElementNullability(substitutor.substitute(parameterType), parameters[idx]);
          arguments[idx] = adaptMethodRefArgument(interpreter, state, value, methodRef, parameters[idx], nullability);
        }
      }
    }
    return new DfaCallArguments(qualifier, arguments, MutationSignature.fromMethod(method));
  }

  static @NotNull DfaValue adaptMethodRefArgument(@NotNull DataFlowInterpreter interpreter,
                                                  @NotNull DfaMemoryState memState,
                                                  @NotNull DfaValue arg,
                                                  @NotNull PsiMethodReferenceExpression methodRef,
                                                  @NotNull PsiParameter parameter,
                                                  @Nullable Nullability nullability) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
      arg = CheckNotNullInstruction.dereference(interpreter, memState, arg,
                                                NullabilityProblemKind.unboxingMethodRefParameter.problem(methodRef, null));
      return DfaUtil.boxUnbox(arg, parameter.getType());
    }
    if (nullability == Nullability.NOT_NULL) {
      return CheckNotNullInstruction.dereference(interpreter, memState, arg,
                                                 NullabilityProblemKind.passingToNotNullMethodRefParameter.problem(methodRef, null));
    }
    if (nullability == Nullability.UNKNOWN) {
      CheckNotNullInstruction.checkNotNullable(interpreter, memState, arg,
                                               NullabilityProblemKind.passingToNonAnnotatedMethodRefParameter.problem(methodRef, null));
    }
    return arg;
  }
}

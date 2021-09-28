// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

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
import org.jetbrains.annotations.NotNull;

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
    final DfaValue qualifier = stateBefore.pop();
    JavaDfaHelpers.dropLocality(qualifier, stateBefore);
    handleMethodReference(qualifier, expression, interpreter, stateBefore);
    pushResult(interpreter, stateBefore, JavaDfaHelpers.getFunctionDfType(expression));
    return nextStates(interpreter, stateBefore);
  }

  public String toString() {
    return "METHOD_REF: " + getDfaAnchor();
  }
  
  public @NotNull PsiMethodReferenceExpression getMethodReference() {
    return ((PsiMethodReferenceExpression)((JavaExpressionAnchor)Objects.requireNonNull(getDfaAnchor())).getExpression());
  }

  private static void handleMethodReference(DfaValue qualifier,
                                            PsiMethodReferenceExpression methodRef,
                                            DataFlowInterpreter interpreter,
                                            DfaMemoryState state) {
    PsiType functionalInterfaceType = methodRef.getFunctionalInterfaceType();
    if (functionalInterfaceType == null) return;
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (sam == null || PsiType.VOID.equals(sam.getReturnType())) return;
    JavaResolveResult resolveResult = methodRef.advancedResolve(false);
    PsiMethod method = tryCast(resolveResult.getElement(), PsiMethod.class);
    if (method == null || !JavaMethodContractUtil.isPure(method)) return;
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    DfaCallArguments callArguments = getMethodReferenceCallArguments(methodRef, qualifier, interpreter, sam, method, substitutor);
    CheckNotNullInstruction.dereference(interpreter, state, callArguments.getQualifier(), NullabilityProblemKind.callMethodRefNPE.problem(methodRef, null));
    if (contracts.isEmpty()) return;
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

  private static @NotNull DfaCallArguments getMethodReferenceCallArguments(PsiMethodReferenceExpression methodRef,
                                                                           DfaValue qualifier,
                                                                           DataFlowInterpreter interpreter,
                                                                           PsiMethod sam,
                                                                           PsiMethod method,
                                                                           PsiSubstitutor substitutor) {
    PsiParameter[] samParameters = sam.getParameterList().getParameters();
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    boolean instanceBound = !isStatic && !PsiMethodReferenceUtil.isStaticallyReferenced(methodRef);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    DfaValue[] arguments = new DfaValue[parameters.length];
    Arrays.fill(arguments, interpreter.getFactory().getUnknown());
    for (int i = 0; i < samParameters.length; i++) {
      DfaValue value = interpreter.getFactory().fromDfType(
        typedObject(substitutor.substitute(samParameters[i].getType()), DfaPsiUtil.getFunctionalParameterNullability(methodRef, i)));
      if (i == 0 && !isStatic && !instanceBound) {
        qualifier = value;
      }
      else {
        int idx = i - ((isStatic || instanceBound) ? 0 : 1);
        if (idx >= arguments.length) break;
        PsiType parameterType = parameters[idx].getType();
        if (!(parameterType instanceof PsiEllipsisType)) {
          arguments[idx] = DfaUtil.boxUnbox(value, parameterType);
        }
      }
    }
    return new DfaCallArguments(qualifier, arguments, MutationSignature.fromMethod(method));
  }
}

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
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
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
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    PsiMethodReferenceExpression expression = getMethodReference();
    final DfaValue qualifier = stateBefore.pop();
    JavaDfaHelpers.dropLocality(qualifier, stateBefore);
    handleMethodReference(qualifier, expression, runner, stateBefore);
    pushResult(runner, stateBefore, typedObject(expression.getFunctionalInterfaceType(), Nullability.NOT_NULL));
    return nextStates(runner, stateBefore);
  }

  public String toString() {
    return "METHOD_REF: " + getDfaAnchor();
  }
  
  public @NotNull PsiMethodReferenceExpression getMethodReference() {
    return ((PsiMethodReferenceExpression)((JavaExpressionAnchor)Objects.requireNonNull(getDfaAnchor())).getExpression());
  }

  private static void handleMethodReference(DfaValue qualifier,
                                            PsiMethodReferenceExpression methodRef,
                                            DataFlowRunner runner,
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
    DfaCallArguments callArguments = getMethodReferenceCallArguments(methodRef, qualifier, runner, sam, method, substitutor);
    CheckNotNullInstruction.dereference(runner, state, callArguments.getQualifier(), NullabilityProblemKind.callMethodRefNPE.problem(methodRef, null));
    if (contracts.isEmpty()) return;
    PsiType returnType = substitutor.substitute(method.getReturnType());
    DfaValue defaultResult = runner.getFactory().fromDfType(typedObject(returnType, DfaPsiUtil.getElementNullability(returnType, method)));
    Set<DfaCallState> currentStates = Collections.singleton(new DfaCallState(state.createClosureState(), callArguments, defaultResult));
    JavaMethodReferenceReturnAnchor anchor = new JavaMethodReferenceReturnAnchor(methodRef);
    DfaValue[] args = callArguments.toArray();
    for (MethodContract contract : contracts) {
      Set<DfaMemoryState> results = new HashSet<>();
      currentStates = MethodCallInstruction.addContractResults(contract, currentStates, runner.getFactory(), results);
      for (DfaMemoryState result : results) {
        DfaValue value = result.pop();
        runner.getInterceptor().beforePush(args, value, anchor, result);
        result.push(value);
      }
    }
    for (DfaCallState currentState: currentStates) {
      runner.getInterceptor().beforePush(args, defaultResult, anchor, currentState.getMemoryState());
      currentState.getMemoryState().push(defaultResult);
    }
  }

  private static @NotNull DfaCallArguments getMethodReferenceCallArguments(PsiMethodReferenceExpression methodRef,
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
    Arrays.fill(arguments, runner.getFactory().getUnknown());
    for (int i = 0; i < samParameters.length; i++) {
      DfaValue value = runner.getFactory().fromDfType(
        typedObject(substitutor.substitute(samParameters[i].getType()), DfaPsiUtil.getFunctionalParameterNullability(methodRef, i)));
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
    return new DfaCallArguments(qualifier, arguments, MutationSignature.fromMethod(method));
  }
}

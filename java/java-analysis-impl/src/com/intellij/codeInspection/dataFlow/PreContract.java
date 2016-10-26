/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.ContractInferenceInterpreter.*;
import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*;

/**
 * @author peter
 */
public abstract class PreContract {

  @NotNull
  abstract List<MethodContract> toContracts(@NotNull PsiMethod method, @NotNull PsiCodeBlock body);

}

class KnownContract extends PreContract {
  private final MethodContract myKnownContract;

  KnownContract(@NotNull MethodContract knownContract) {
    myKnownContract = knownContract;
  }

  @NotNull
  MethodContract getContract() {
    return myKnownContract;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method, @NotNull PsiCodeBlock body) {
    return Collections.singletonList(myKnownContract);
  }
}

class DelegationContract extends PreContract {

  private final ExpressionRange myExpression;
  private final boolean myNegated;

  DelegationContract(ExpressionRange expression, boolean negated) {
    myExpression = expression;
    myNegated = negated;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method, @NotNull PsiCodeBlock body) {
    PsiMethodCallExpression call = (PsiMethodCallExpression)myExpression.restoreExpression(body);
    if (call == null) return Collections.emptyList();

    JavaResolveResult result = call.resolveMethodGenerics();

    final PsiMethod targetMethod = (PsiMethod)result.getElement();
    if (targetMethod == null) return Collections.emptyList();

    final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
    final PsiExpression[] arguments = call.getArgumentList().getExpressions();
    final boolean varArgCall = MethodCallInstruction.isVarArgCall(targetMethod, result.getSubstitutor(), arguments, parameters);

    List<MethodContract> fromDelegate = ContainerUtil.mapNotNull(ControlFlowAnalyzer.getMethodContracts(targetMethod),
                                                                 dc -> convertDelegatedMethodContract(method, parameters, arguments, varArgCall, dc));
    if (NullableNotNullManager.isNotNull(targetMethod)) {
      return ContainerUtil.concat(ContainerUtil.map(fromDelegate, DelegationContract::returnNotNull),
                                  Collections.singletonList(new MethodContract(emptyConstraints(method), NOT_NULL_VALUE)));
    }
    return fromDelegate;
  }

  @Nullable
  private MethodContract convertDelegatedMethodContract(@NotNull PsiMethod callerMethod,
                                                        PsiParameter[] targetParameters,
                                                        PsiExpression[] callArguments,
                                                        boolean varArgCall,
                                                        MethodContract targetContract) {
    ValueConstraint[] answer = emptyConstraints(callerMethod);
    for (int i = 0; i < targetContract.arguments.length; i++) {
      if (i >= callArguments.length) return null;
      ValueConstraint argConstraint = targetContract.arguments[i];
      if (argConstraint != ANY_VALUE) {
        if (varArgCall && i >= targetParameters.length - 1) {
          if (argConstraint == NULL_VALUE) {
            return null;
          }
          break;
        }

        PsiExpression argument = callArguments[i];
        int paramIndex = resolveParameter(callerMethod, argument);
        if (paramIndex >= 0) {
          answer = withConstraint(answer, paramIndex, argConstraint, callerMethod);
          if (answer == null) {
            return null;
          }
        }
        else if (argConstraint != getLiteralConstraint(argument)) {
          return null;
        }
      }
    }
    ValueConstraint returnValue = myNegated ? negateConstraint(targetContract.returnValue) : targetContract.returnValue;
    return answer == null ? null : new MethodContract(answer, returnValue);
  }

  private static ValueConstraint[] emptyConstraints(@NotNull PsiMethod method) {
    return MethodContract.createConstraintArray(method.getParameterList().getParametersCount());
  }

  @NotNull
  private static MethodContract returnNotNull(MethodContract mc) {
    return mc.returnValue == THROW_EXCEPTION ? mc : new MethodContract(mc.arguments, NOT_NULL_VALUE);
  }

  private static ValueConstraint getLiteralConstraint(PsiExpression argument) {
    return argument instanceof PsiLiteralExpression ? ContractInferenceInterpreter.getLiteralConstraint(argument.getFirstChild().getNode().getElementType()) : null;
  }

  private static int resolveParameter(@NotNull PsiMethod method, PsiExpression expr) {
    PsiElement target = expr instanceof PsiReferenceExpression && !((PsiReferenceExpression)expr).isQualified()
                        ? ((PsiReferenceExpression)expr).resolve() : null;
    return target instanceof PsiParameter && target.getParent() == method.getParameterList()
           ? method.getParameterList().getParameterIndex((PsiParameter)target) : -1;
  }
}

class SideEffectFilter extends PreContract {
  private final List<ExpressionRange> myExpressionsToCheck;
  private final List<PreContract> myContracts;

  SideEffectFilter(List<ExpressionRange> expressionsToCheck, List<PreContract> contracts) {
    myExpressionsToCheck = expressionsToCheck;
    myContracts = contracts;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method, @NotNull PsiCodeBlock body) {
    if (ContainerUtil.exists(myExpressionsToCheck, d -> mayHaveSideEffects(body, d))) {
      return Collections.emptyList();
    }
    return ContainerUtil.concat(myContracts, c -> c.toContracts(method, body));
  }

  private static boolean mayHaveSideEffects(PsiCodeBlock body, ExpressionRange range) {
    PsiExpression exp = range.restoreExpression(body);
    return exp != null && SideEffectChecker.mayHaveSideEffects(exp);
  }
}

class NegatingContract extends PreContract {
  private final PreContract myNegated;

  private NegatingContract(PreContract negated) {
    myNegated = negated;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method, @NotNull PsiCodeBlock body) {
    return ContainerUtil.mapNotNull(myNegated.toContracts(method, body), NegatingContract::negateContract);
  }

  @Nullable
  static PreContract negate(@NotNull PreContract contract) {
    if (contract instanceof KnownContract) {
      MethodContract negated = negateContract(((KnownContract)contract).getContract());
      return negated == null ? null : new KnownContract(negated);
    }
    return new NegatingContract(contract);
  }

  @Nullable
  private static MethodContract negateContract(MethodContract c) {
    ValueConstraint ret = c.returnValue;
    return ret == TRUE_VALUE || ret == FALSE_VALUE ? new MethodContract(c.arguments, negateConstraint(ret)) : null;
  }
}

class MethodCallContract extends PreContract {
  private final ExpressionRange myCall;
  private final List<ValueConstraint[]> myStates;

  MethodCallContract(ExpressionRange call, List<ValueConstraint[]> states) {
    myCall = call;
    myStates = states;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method, @NotNull PsiCodeBlock body) {
    PsiMethodCallExpression call = (PsiMethodCallExpression)myCall.restoreExpression(body);
    PsiMethod target = call == null ? null : call.resolveMethod();
    if (target != null && NullableNotNullManager.isNotNull(target)) {
      return ContractInferenceInterpreter.toContracts(myStates, NOT_NULL_VALUE);
    }
    return Collections.emptyList();
  }
}

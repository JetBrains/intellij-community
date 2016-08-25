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
  abstract List<MethodContract> toContracts(@NotNull PsiMethod method);

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
  List<MethodContract> toContracts(@NotNull PsiMethod method) {
    return Collections.singletonList(myKnownContract);
  }
}

class DelegationContract extends PreContract {

  private final PsiMethodCallExpression myExpression;
  private final boolean myNegated;

  DelegationContract(PsiMethodCallExpression expression, boolean negated) {
    myExpression = expression;
    myNegated = negated;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method) {
    JavaResolveResult result = myExpression.resolveMethodGenerics();

    final PsiMethod targetMethod = (PsiMethod)result.getElement();
    if (targetMethod == null) return Collections.emptyList();

    final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
    final PsiExpression[] arguments = myExpression.getArgumentList().getExpressions();
    final boolean varArgCall = MethodCallInstruction.isVarArgCall(targetMethod, result.getSubstitutor(), arguments, parameters);

    final boolean notNull = NullableNotNullManager.isNotNull(targetMethod);
    ValueConstraint[] emptyConstraints = MethodContract.createConstraintArray(method.getParameterList().getParametersCount());
    List<MethodContract> fromDelegate = ContainerUtil.mapNotNull(ControlFlowAnalyzer.getMethodContracts(targetMethod), delegateContract -> {
      ValueConstraint[] answer = emptyConstraints;
      for (int i = 0; i < delegateContract.arguments.length; i++) {
        if (i >= arguments.length) return null;
        ValueConstraint argConstraint = delegateContract.arguments[i];
        if (argConstraint != ANY_VALUE) {
          if (varArgCall && i >= parameters.length - 1) {
            if (argConstraint == NULL_VALUE) {
              return null;
            }
            break;
          }

          int paramIndex = resolveParameter(arguments[i], method);
          if (paramIndex < 0) {
            if (argConstraint != getLiteralConstraint(arguments[i])) {
              return null;
            }
          }
          else {
            answer = withConstraint(answer, paramIndex, argConstraint, method);
            if (answer == null) {
              return null;
            }
          }
        }
      }
      ValueConstraint returnValue = myNegated ? negateConstraint(delegateContract.returnValue) : delegateContract.returnValue;
      if (notNull && returnValue != THROW_EXCEPTION) {
        returnValue = NOT_NULL_VALUE;
      }
      return answer == null ? null : new MethodContract(answer, returnValue);
    });
    if (notNull) {
      return ContainerUtil.concat(fromDelegate, Collections.singletonList(new MethodContract(emptyConstraints, NOT_NULL_VALUE)));
    }
    return fromDelegate;
  }
}

class SideEffectFilter extends PreContract {
  private final List<PsiExpression> myExpressionsToCheck;
  private final List<PreContract> myContracts;

  SideEffectFilter(List<PsiExpression> expressionsToCheck, List<PreContract> contracts) {
    myExpressionsToCheck = expressionsToCheck;
    myContracts = contracts;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method) {
    if (ContainerUtil.exists(myExpressionsToCheck, d -> SideEffectChecker.mayHaveSideEffects(d))) {
      return Collections.emptyList();
    }
    return ContainerUtil.concat(myContracts, c -> c.toContracts(method));
  }

}

class NegatingContract extends PreContract {
  private final PreContract myNegated;

  private NegatingContract(PreContract negated) {
    myNegated = negated;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method) {
    return ContainerUtil.mapNotNull(myNegated.toContracts(method), NegatingContract::negateContract);
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
  private final PsiMethodCallExpression myCall;
  private final List<ValueConstraint[]> myStates;

  MethodCallContract(PsiMethodCallExpression call, List<ValueConstraint[]> states) {
    myCall = call;
    myStates = states;
  }

  @NotNull
  @Override
  List<MethodContract> toContracts(@NotNull PsiMethod method) {
    PsiMethod target = myCall.resolveMethod();
    if (target != null && NullableNotNullManager.isNotNull(target)) {
      return ContractInferenceInterpreter.toContracts(myStates, NOT_NULL_VALUE);
    }
    return Collections.emptyList();
  }
}

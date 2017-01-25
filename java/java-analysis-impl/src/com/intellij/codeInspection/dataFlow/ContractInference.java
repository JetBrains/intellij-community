/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*;

/**
 * @author peter
 */
public class ContractInference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ContractInferenceInterpreter");
  public static final int MAX_CONTRACT_COUNT = 10;

  @NotNull
  public static List<MethodContract> inferContracts(@NotNull final PsiMethod method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method)) {
      return Collections.emptyList();
    }

    return CachedValuesManager.getCachedValue(method, () -> {
      MethodData data = ContractInferenceIndexKt.getIndexedData(method);
      List<PreContract> preContracts = data == null ? Collections.emptyList() : data.getContracts();
      List<MethodContract> result = RecursionManager.doPreventingRecursion(method, true, () -> postProcessContracts(method, data, preContracts));
      if (result == null) result = Collections.emptyList();
      return CachedValueProvider.Result.create(result, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  @NotNull
  private static List<MethodContract> postProcessContracts(@NotNull PsiMethod method, MethodData data, List<PreContract> rawContracts) {
    List<MethodContract> contracts = ContainerUtil.concat(rawContracts, c -> c.toContracts(method, data.methodBody(method)));
    if (contracts.isEmpty()) return Collections.emptyList();
    
    final PsiType returnType = method.getReturnType();
    if (returnType != null && !(returnType instanceof PsiPrimitiveType)) {
      contracts = boxReturnValues(contracts);
    }
    List<MethodContract> compatible = ContainerUtil.filter(contracts, contract -> isContractCompatibleWithMethod(method, returnType, contract));
    if (compatible.size() > MAX_CONTRACT_COUNT) {
      LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(method) + ", shrinking the list");
      return compatible.subList(0, MAX_CONTRACT_COUNT);
    }
    return compatible;
  }

  private static boolean isContractCompatibleWithMethod(@NotNull PsiMethod method, PsiType returnType, MethodContract contract) {
    if (hasContradictoryExplicitParameterNullity(method, contract)) return false;
    if (isReturnNullitySpecifiedExplicitly(method, contract)) return false;
    if (isContradictingExplicitNullableReturn(method, contract)) return false;
    return InferenceFromSourceUtil.isReturnTypeCompatible(returnType, contract.returnValue);
  }

  private static boolean hasContradictoryExplicitParameterNullity(@NotNull PsiMethod method, MethodContract contract) {
    for (int i = 0; i < contract.arguments.length; i++) {
      if (contract.arguments[i] == NULL_VALUE && NullableNotNullManager.isNotNull(method.getParameterList().getParameters()[i])) {
        return true;
      }
    }
    return false;
  }

  private static boolean isContradictingExplicitNullableReturn(@NotNull PsiMethod method, MethodContract contract) {
    return contract.returnValue == NOT_NULL_VALUE &&
           Arrays.stream(contract.arguments).allMatch(c -> c == ANY_VALUE) &&
           NullableNotNullManager.getInstance(method.getProject()).isNullable(method, false);
  }

  private static boolean isReturnNullitySpecifiedExplicitly(@NotNull PsiMethod method, MethodContract contract) {
    if (contract.returnValue != NOT_NULL_VALUE && contract.returnValue != NULL_VALUE) {
      return false; // spare expensive nullity check
    }
    return NullableNotNullManager.getInstance(method.getProject()).isNotNull(method, false);
  }

  @NotNull
  private static List<MethodContract> boxReturnValues(List<MethodContract> contracts) {
    return ContainerUtil.mapNotNull(contracts, contract -> {
      if (contract.returnValue == FALSE_VALUE || contract.returnValue == TRUE_VALUE) {
        return new MethodContract(contract.arguments, NOT_NULL_VALUE);
      }
      return contract;
    });
  }

}
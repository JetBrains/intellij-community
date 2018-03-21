// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.*;

/**
 * A facade for all inference algorithms which work on Java source code (Light AST) and cache results in the index.
 */
public class JavaSourceInference {
  public static final int MAX_CONTRACT_COUNT = 10;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.inference.JavaSourceInference");

  /**
   * Infer method return type nullity
   *
   * @param method method to analyze
   * @return inferred return type nullity; {@link Nullness#UNKNOWN} if cannot be inferred or non-applicable
   */
  @NotNull
  public static Nullness inferNullity(PsiMethodImpl method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method)) {
      return Nullness.UNKNOWN;
    }

    PsiType type = method.getReturnType();
    if (type == null || type instanceof PsiPrimitiveType) {
      return Nullness.UNKNOWN;
    }

    return CachedValuesManager.getCachedValue(method, () -> {
      MethodData data = ContractInferenceIndexKt.getIndexedData(method);
      MethodReturnInferenceResult result = data == null ? null : data.getMethodReturn();
      Nullness nullness = result == null ? null : RecursionManager
        .doPreventingRecursion(method, true, () -> result.getNullness(method, data.methodBody(method)));
      if (nullness == null) nullness = Nullness.UNKNOWN;
      return CachedValueProvider.Result.create(nullness, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  /**
   * Infer method parameter nullity
   *
   * @param parameter parameter to analyze
   * @return inferred parameter nullity; {@link Nullness#UNKNOWN} if cannot be inferred or non-applicable
   */
  public static Nullness inferNullity(@NotNull PsiParameter parameter) {
    if (!parameter.isPhysical() || parameter.getType() instanceof PsiPrimitiveType) return Nullness.UNKNOWN;
    PsiParameterList parent = ObjectUtils.tryCast(parameter.getParent(), PsiParameterList.class);
    if (parent == null) return Nullness.UNKNOWN;
    PsiMethodImpl method = ObjectUtils.tryCast(parent.getParent(), PsiMethodImpl.class);
    if (method == null || !InferenceFromSourceUtil.shouldInferFromSource(method)) return Nullness.UNKNOWN;

    return CachedValuesManager.getCachedValue(parameter, () -> {
      Nullness nullness = Nullness.UNKNOWN;
      MethodData data = ContractInferenceIndexKt.getIndexedData(method);
      if (data != null) {
        BitSet notNullParameters = data.getNotNullParameters();
        if (!notNullParameters.isEmpty()) {
          int index = ArrayUtil.indexOf(parent.getParameters(), parameter);
          if (notNullParameters.get(index)) {
            nullness = Nullness.NOT_NULL;
          }
        }
      }
      return CachedValueProvider.Result.create(nullness, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  /**
   * Infer method return type mutability
   *
   * @param method method to analyze
   * @return inferred return type mutability; {@link Mutability#UNKNOWN} if cannot be inferred or non-applicable
   */
  @NotNull
  public static Mutability inferMutability(PsiMethodImpl method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method)) {
      return Mutability.UNKNOWN;
    }

    PsiType type = method.getReturnType();
    if (type == null || ClassUtils.isImmutable(type)) {
      return Mutability.UNKNOWN;
    }

    return CachedValuesManager.getCachedValue(method, () -> {
      MethodData data = ContractInferenceIndexKt.getIndexedData(method);
      MethodReturnInferenceResult result = data == null ? null : data.getMethodReturn();
      Mutability mutability = result == null ? null : RecursionManager
        .doPreventingRecursion(method, true, () -> result.getMutability(method, data.methodBody(method)));
      if (mutability == null) mutability = Mutability.UNKNOWN;
      return CachedValueProvider.Result.create(mutability, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  /**
   * Infer method contracts
   *
   * @param method method to analyze
   * @return inferred contracts; empty list of cannot be inferred or non-applicable
   */
  @NotNull
  public static List<StandardMethodContract> inferContracts(@NotNull PsiMethodImpl method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method)) {
      return Collections.emptyList();
    }

    return CachedValuesManager.getCachedValue(method, () -> {
      MethodData data = ContractInferenceIndexKt.getIndexedData(method);
      List<PreContract> preContracts = data == null ? Collections.emptyList() : data.getContracts();
      List<StandardMethodContract> result = RecursionManager.doPreventingRecursion(method, true, () -> postProcessContracts(method, data, preContracts));
      if (result == null) result = Collections.emptyList();
      return CachedValueProvider.Result.create(result, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  /**
   * Infer method purity
   *
   * @param method method to analyze
   * @return true if method was inferred to be pure; false if method is not pure or cannot be analyzed
   */
  public static boolean inferPurity(@NotNull PsiMethodImpl method) {
    if (!InferenceFromSourceUtil.shouldInferFromSource(method) ||
        PsiType.VOID.equals(method.getReturnType()) ||
        method.isConstructor()) {
      return false;
    }

    return CachedValuesManager.getCachedValue(method, () -> {
      MethodData data = ContractInferenceIndexKt.getIndexedData(method);
      PurityInferenceResult result = data == null ? null : data.getPurity();
      Boolean pure = RecursionManager.doPreventingRecursion(method, true, () -> result != null && result.isPure(method, data.methodBody(method)));
      return CachedValueProvider.Result.create(pure == Boolean.TRUE, method);
    });
  }

  @NotNull
  private static List<StandardMethodContract> postProcessContracts(@NotNull PsiMethodImpl method, MethodData data, List<PreContract> rawContracts) {
    List<StandardMethodContract> contracts = ContainerUtil.concat(rawContracts, c -> c.toContracts(method, data.methodBody(method)));
    if (contracts.isEmpty()) return Collections.emptyList();

    final PsiType returnType = method.getReturnType();
    if (returnType != null && !(returnType instanceof PsiPrimitiveType)) {
      contracts = boxReturnValues(contracts);
    }
    List<StandardMethodContract> compatible = ContainerUtil.filter(contracts, contract -> isContractCompatibleWithMethod(method, returnType, contract));
    if (compatible.size() > MAX_CONTRACT_COUNT) {
      LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(method) + ", shrinking the list");
      return compatible.subList(0, MAX_CONTRACT_COUNT);
    }
    return compatible;
  }

  private static boolean isContractCompatibleWithMethod(@NotNull PsiMethod method, PsiType returnType, StandardMethodContract contract) {
    if (hasContradictoryExplicitParameterNullity(method, contract)) return false;
    if (isReturnNullitySpecifiedExplicitly(method, contract)) return false;
    if (isContradictingExplicitNullableReturn(method, contract)) return false;
    return contract.returnValue.isReturnTypeCompatible(returnType);
  }

  private static boolean hasContradictoryExplicitParameterNullity(@NotNull PsiMethod method, StandardMethodContract contract) {
    for (int i = 0; i < contract.arguments.length; i++) {
      if (contract.arguments[i] == NULL_VALUE && NullableNotNullManager.isNotNull(method.getParameterList().getParameters()[i])) {
        return true;
      }
    }
    return false;
  }

  private static boolean isContradictingExplicitNullableReturn(@NotNull PsiMethod method, StandardMethodContract contract) {
    return contract.returnValue == NOT_NULL_VALUE &&
           Arrays.stream(contract.arguments).allMatch(c -> c == ANY_VALUE) &&
           NullableNotNullManager.getInstance(method.getProject()).isNullable(method, false);
  }

  private static boolean isReturnNullitySpecifiedExplicitly(@NotNull PsiMethod method, StandardMethodContract contract) {
    if (contract.returnValue != NOT_NULL_VALUE && contract.returnValue != NULL_VALUE) {
      return false; // spare expensive nullity check
    }
    return NullableNotNullManager.getInstance(method.getProject()).isNotNull(method, false);
  }

  @NotNull
  private static List<StandardMethodContract> boxReturnValues(List<StandardMethodContract> contracts) {
    return ContainerUtil.mapNotNull(contracts, contract -> {
      if (contract.returnValue == FALSE_VALUE || contract.returnValue == TRUE_VALUE) {
        return new StandardMethodContract(contract.arguments, NOT_NULL_VALUE);
      }
      return contract;
    });
  }
}

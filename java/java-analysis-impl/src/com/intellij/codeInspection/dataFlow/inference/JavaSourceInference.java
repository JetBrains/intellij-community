// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.NULL_VALUE;

/**
 * A facade for all inference algorithms which work on Java source code (Light AST) and cache results in the index.
 */
public class JavaSourceInference {
  public static final int MAX_CONTRACT_COUNT = 10;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.inference.JavaSourceInference");

  private static class MethodInferenceData {
    static final MethodInferenceData UNKNOWN =
      new MethodInferenceData(Mutability.UNKNOWN, Nullability.UNKNOWN, Collections.emptyList(), false, new BitSet());
    
    final @NotNull Mutability myMutability;
    final @NotNull Nullability myNullability;
    final @NotNull List<StandardMethodContract> myContracts;
    final boolean myPure;
    final @NotNull BitSet myNotNullParameters;

    MethodInferenceData(@NotNull Mutability mutability,
                        @NotNull Nullability nullability,
                        @NotNull List<StandardMethodContract> contracts,
                        boolean pure,
                        @NotNull BitSet parameters) {
      myMutability = mutability;
      myNullability = nullability;
      myContracts = contracts;
      myPure = pure;
      myNotNullParameters = parameters;
    }
  }

  @NotNull
  private static MethodInferenceData infer(PsiMethodImpl method) {
    InferenceFromSourceUtil.InferenceMode mode = InferenceFromSourceUtil.getInferenceMode(method);
    if (mode == InferenceFromSourceUtil.InferenceMode.DISABLED ||
        mode == InferenceFromSourceUtil.InferenceMode.PARAMETERS && method.getParameterList().isEmpty()) {
      return MethodInferenceData.UNKNOWN;
    }

    MethodData data = ContractInferenceIndexKt.getIndexedData(method);
    if (data == null) return MethodInferenceData.UNKNOWN;
    BitSet parameters = data.getNotNullParameters();
    if (mode == InferenceFromSourceUtil.InferenceMode.PARAMETERS) {
      return parameters.isEmpty() ? MethodInferenceData.UNKNOWN :
             new MethodInferenceData(Mutability.UNKNOWN, Nullability.UNKNOWN, Collections.emptyList(), false, parameters);
    }
    
    Function0<PsiCodeBlock> body = data.methodBody(method);
    PsiType type = method.getReturnType();
    
    Nullability nullability = Nullability.UNKNOWN;
    Mutability mutability = Mutability.UNKNOWN;
    if (type != null && !(type instanceof PsiPrimitiveType)) {
      MethodReturnInferenceResult result = data.getMethodReturn();
      if (result != null) {
        nullability = RecursionManager.doPreventingRecursion(method, true, () -> result.getNullability(method, body));
        if (nullability == null) nullability = Nullability.UNKNOWN;
        if (!ClassUtils.isImmutable(type, false)) {
          mutability = RecursionManager.doPreventingRecursion(method, true, () -> result.getMutability(method, body));
          if (mutability == null) mutability = Mutability.UNKNOWN;
        }
      }
    }
    
    boolean pure = false;
    if (!PsiType.VOID.equals(type)) {
      PurityInferenceResult result = data.getPurity();
      if (result != null) {
        pure = Boolean.TRUE.equals(RecursionManager.doPreventingRecursion(method, true, () -> result.isPure(method, body)));
      }
    }

    List<PreContract> preContracts = data.getContracts();
    List<StandardMethodContract> contracts = RecursionManager.doPreventingRecursion(
      method, true, () -> postProcessContracts(method, data, preContracts));
    if (contracts == null) contracts = Collections.emptyList();

    return new MethodInferenceData(mutability, nullability, contracts, pure, parameters);
  }
  
  @NotNull
  private static MethodInferenceData getInferenceData(PsiMethod method) {
    if (!(method instanceof PsiMethodImpl)) {
      return MethodInferenceData.UNKNOWN;
    }
    return CachedValuesManager.getCachedValue(
      method, () -> CachedValueProvider.Result.create(infer((PsiMethodImpl)method), method, PsiModificationTracker.MODIFICATION_COUNT));
  }

  /**
   * Infer method return type nullability
   *
   * @param method method to analyze
   * @return inferred return type nullability; {@link Nullability#UNKNOWN} if cannot be inferred or non-applicable
   */
  @NotNull
  public static Nullability inferNullability(PsiMethodImpl method) {
    return getInferenceData(method).myNullability;
  }

  /**
   * Infer method parameter nullability
   *
   * @param parameter parameter to analyze
   * @return inferred parameter nullability; {@link Nullability#UNKNOWN} if cannot be inferred or non-applicable
   */
  public static Nullability inferNullability(@NotNull PsiParameter parameter) {
    if (!parameter.isPhysical() || parameter.getType() instanceof PsiPrimitiveType) return Nullability.UNKNOWN;
    PsiParameterList parent = ObjectUtils.tryCast(parameter.getParent(), PsiParameterList.class);
    if (parent == null) return Nullability.UNKNOWN;
    PsiMethodImpl method = ObjectUtils.tryCast(parent.getParent(), PsiMethodImpl.class);
    if (method == null) return Nullability.UNKNOWN;

    BitSet notNullParameters = getInferenceData(method).myNotNullParameters;
    if (!notNullParameters.isEmpty()) {
      int index = parent.getParameterIndex(parameter);
      if (notNullParameters.get(index)) {
        return Nullability.NOT_NULL;
      }
    }
    return Nullability.UNKNOWN;
  }

  /**
   * Infer method return type mutability
   *
   * @param method method to analyze
   * @return inferred return type mutability; {@link Mutability#UNKNOWN} if cannot be inferred or non-applicable
   */
  @NotNull
  public static Mutability inferMutability(PsiMethodImpl method) {
    return getInferenceData(method).myMutability;
  }

  /**
   * Infer method contracts
   *
   * @param method method to analyze
   * @return inferred contracts; empty list of cannot be inferred or non-applicable
   */
  @NotNull
  public static List<StandardMethodContract> inferContracts(@NotNull PsiMethodImpl method) {
    return getInferenceData(method).myContracts;
  }

  /**
   * Infer method purity
   *
   * @param method method to analyze
   * @return true if method was inferred to be pure; false if method is not pure or cannot be analyzed
   */
  public static boolean inferPurity(@NotNull PsiMethodImpl method) {
    return getInferenceData(method).myPure;
  }

  @NotNull
  private static List<StandardMethodContract> postProcessContracts(@NotNull PsiMethodImpl method, MethodData data, List<PreContract> rawContracts) {
    List<StandardMethodContract> contracts = ContainerUtil.concat(rawContracts, c -> c.toContracts(method, data.methodBody(method)));
    if (contracts.isEmpty()) return Collections.emptyList();
    if (contracts.size() == 2) {
      StandardMethodContract collapsed = contracts.get(0).tryCollapse(contracts.get(1));
      if (collapsed != null) {
        contracts = Collections.singletonList(collapsed);
      }
    }

    final PsiType returnType = method.getReturnType();
    if (returnType != null && !(returnType instanceof PsiPrimitiveType)) {
      contracts = boxReturnValues(contracts);
    }
    List<StandardMethodContract> compatible = ContainerUtil.filter(contracts, contract -> isContractCompatibleWithMethod(method, contract));
    if (compatible.size() > MAX_CONTRACT_COUNT) {
      LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(method) + ", shrinking the list");
      return compatible.subList(0, MAX_CONTRACT_COUNT);
    }
    return compatible;
  }

  private static boolean isContractCompatibleWithMethod(@NotNull PsiMethod method, StandardMethodContract contract) {
    if (hasContradictoryExplicitParameterNullity(method, contract)) return false;
    if (isReturnNullitySpecifiedExplicitly(method, contract)) return false;
    if (isContradictingExplicitNullableReturn(method, contract)) return false;
    return contract.getReturnValue().isMethodCompatible(method);
  }

  private static boolean hasContradictoryExplicitParameterNullity(@NotNull PsiMethod method, StandardMethodContract contract) {
    for (int i = 0; i < contract.getParameterCount(); i++) {
      if (contract.getParameterConstraint(i) == NULL_VALUE && NullableNotNullManager.isNotNull(method.getParameterList().getParameters()[i])) {
        return true;
      }
    }
    return false;
  }

  private static boolean isContradictingExplicitNullableReturn(@NotNull PsiMethod method, StandardMethodContract contract) {
    return contract.getReturnValue().isNotNull() && contract.isTrivial() &&
           NullableNotNullManager.getInstance(method.getProject()).isNullable(method, false);
  }

  private static boolean isReturnNullitySpecifiedExplicitly(@NotNull PsiMethod method, StandardMethodContract contract) {
    if (!contract.getReturnValue().equals(ContractReturnValue.returnNotNull()) && !contract.getReturnValue().isNull()) {
      return false; // spare expensive nullity check
    }
    return NullableNotNullManager.getInstance(method.getProject()).isNotNull(method, false);
  }

  @NotNull
  private static List<StandardMethodContract> boxReturnValues(List<StandardMethodContract> contracts) {
    return ContainerUtil.mapNotNull(contracts, contract -> {
      if (contract.getReturnValue().isBoolean()) {
        return contract.withReturnValue(ContractReturnValue.returnNotNull());
      }
      return contract;
    });
  }
}

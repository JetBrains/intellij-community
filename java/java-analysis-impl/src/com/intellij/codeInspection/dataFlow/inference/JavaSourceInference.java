// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.NULL_VALUE;

/**
 * A facade for all inference algorithms which work on Java source code (Light AST) and cache results in the index.
 */
public final class JavaSourceInference {
  public static final int MAX_CONTRACT_COUNT = 10;
  private static final Logger LOG = Logger.getInstance(JavaSourceInference.class);

  enum InferenceMode {
    DISABLED, ENABLED, PARAMETERS
  }

  private record MethodInferenceData(@NotNull Mutability mutability, @NotNull Nullability nullability,
                                     @NotNull List<StandardMethodContract> contracts, @NotNull MutationSignature signature,
                                     @NotNull BitSet notNullParameters) {
      static final MethodInferenceData UNKNOWN =
        new MethodInferenceData(Mutability.UNKNOWN, Nullability.UNKNOWN, Collections.emptyList(), MutationSignature.unknown(), new BitSet());
  }

  @NotNull
  private static MethodInferenceData infer(PsiMethodImpl method) {
    InferenceMode mode = getInferenceMode(method);
    if (mode == InferenceMode.DISABLED ||
        mode == InferenceMode.PARAMETERS && method.getParameterList().isEmpty()) {
      return MethodInferenceData.UNKNOWN;
    }

    MethodData data = ContractInferenceIndexKt.getIndexedData(method);
    if (data == null) return MethodInferenceData.UNKNOWN;
    BitSet notNullParameters = data.getNotNullParameters();
    if (mode == InferenceMode.PARAMETERS) {
      // Infer parameters nullability only (for unstable methods)
      return notNullParameters.isEmpty() ? MethodInferenceData.UNKNOWN :
             new MethodInferenceData(Mutability.UNKNOWN, Nullability.UNKNOWN, Collections.emptyList(),
                                     MutationSignature.unknown(), notNullParameters);
    }

    Nullability nullability = findNullability(method, data);
    Mutability mutability = findMutability(method, data);
    MutationSignature signature = findMutationSignature(method, data);

    IntPredicate isNotNullParameter = i -> {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
      NullabilityAnnotationInfo parameterInfo = manager.findExplicitNullability(parameters[i]);
      return parameterInfo != null ? parameterInfo.getNullability() == Nullability.NOT_NULL : notNullParameters.get(i);
    };
    List<StandardMethodContract> contracts = findContracts(method, data, nullability, isNotNullParameter);
    if (nullability == Nullability.NULLABLE && ContainerUtil.find(contracts, c -> c.getReturnValue().isNull()) != null) {
      nullability = Nullability.UNKNOWN;
    }

    return new MethodInferenceData(mutability, nullability, contracts, signature, notNullParameters);
  }

  @NotNull
  private static Nullability findNullability(PsiMethodImpl method, MethodData data) {
    PsiType type = method.getReturnType();
    if (type == null || type instanceof PsiPrimitiveType) return Nullability.UNKNOWN;
    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(method.getProject()).findExplicitNullability(method);
    if (info != null) return info.getNullability();
    MethodReturnInferenceResult result = data.getMethodReturn();
    if (result == null) return Nullability.UNKNOWN;
    try {
      Nullability nullability = RecursionManager.doPreventingRecursion(
        method, true, () -> result.getNullability(method, data.methodBody(method)));
      return nullability == null ? Nullability.UNKNOWN : nullability;
    }
    catch (CannotRestoreExpressionException e) {
      throw ContractInferenceIndexKt.handleInconsistency(method, data, e);
    }
  }

  @NotNull
  private static Mutability findMutability(@NotNull PsiMethodImpl method, @NotNull MethodData data) {
    PsiType type = method.getReturnType();
    if (type == null || ClassUtils.isImmutable(type, false)) return Mutability.UNKNOWN;
    MethodReturnInferenceResult result = data.getMethodReturn();
    if (result == null) return Mutability.UNKNOWN;
    try {
      Mutability mutability = RecursionManager.doPreventingRecursion(
        method, true, () -> result.getMutability(method, data.methodBody(method)));
      return mutability == null ? Mutability.UNKNOWN : mutability;
    }
    catch (CannotRestoreExpressionException e) {
      throw ContractInferenceIndexKt.handleInconsistency(method, data, e);
    }
  }

  private static @NotNull MutationSignature findMutationSignature(@NotNull PsiMethodImpl method, @NotNull MethodData data) {
    PurityInferenceResult result = data.getPurity();
    if (result == null) return MutationSignature.unknown();
    try {
      MutationSignature signature =
        RecursionManager.doPreventingRecursion(method, true, () -> result.getMutationSignature(method, data.methodBody(method)));
      return signature == null ? MutationSignature.unknown() : signature;
    }
    catch (CannotRestoreExpressionException e) {
      throw ContractInferenceIndexKt.handleInconsistency(method, data, e);
    }
  }

  @NotNull
  private static List<StandardMethodContract> findContracts(@NotNull PsiMethodImpl method,
                                                            @NotNull MethodData data,
                                                            @NotNull Nullability nullability,
                                                            @NotNull IntPredicate notNullParameter) {
    PsiAnnotation explicitContract = AnnotationUtil.findAnnotationInHierarchy(
      method, Collections.singleton(JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT), true);
    if (explicitContract != null) {
      // Explicit contract may suppress inferred nullability, so parse them
      return JavaMethodContractUtil.parseContracts(method, explicitContract);
    }
    List<PreContract> preContracts = data.getContracts();
    List<StandardMethodContract> contracts;
    try {
      contracts = RecursionManager.doPreventingRecursion(
        method, true, () -> ContainerUtil.concat(preContracts, c -> c.toContracts(method, data.methodBody(method))));
    }
    catch (CannotRestoreExpressionException e) {
      throw ContractInferenceIndexKt.handleInconsistency(method, data, e);
    }
    if (contracts == null || contracts.isEmpty()) return Collections.emptyList();
    if (contracts.size() == 2) {
      StandardMethodContract collapsed = contracts.get(0).tryCollapse(contracts.get(1));
      if (collapsed != null) {
        contracts = Collections.singletonList(collapsed);
      }
    }

    return postProcessContracts(contracts, method, nullability, notNullParameter);
  }

  @NotNull
  private static List<StandardMethodContract> postProcessContracts(List<StandardMethodContract> contracts, @NotNull PsiMethod method,
                                                                   @NotNull Nullability nullability,
                                                                   @NotNull IntPredicate notNullParameter) {
    final PsiType returnType = method.getReturnType();
    if (returnType != null && !(returnType instanceof PsiPrimitiveType)) {
      contracts = boxReturnValues(contracts);
    }
    List<StandardMethodContract> compatible = ContainerUtil.filter(contracts, contract -> {
      for (int i = 0; i < contract.getParameterCount(); i++) {
        if (contract.getParameterConstraint(i) == NULL_VALUE && notNullParameter.test(i)) {
          return false;
        }
      }
      ContractReturnValue retValue = contract.getReturnValue();
      if (nullability == Nullability.NOT_NULL && (retValue.equals(ContractReturnValue.returnNotNull()) || retValue.isNull())) return false;
      if (nullability == Nullability.NULLABLE && retValue.isNotNull() && contract.isTrivial()) return false;
      return retValue.isMethodCompatible(method);
    });
    if (compatible.size() > MAX_CONTRACT_COUNT) {
      LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(method) + ", shrinking the list");
      return compatible.subList(0, MAX_CONTRACT_COUNT);
    }
    return compatible;
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
    return getInferenceData(method).nullability;
  }

  /**
   * Infer method parameter nullability
   *
   * @param parameter parameter to analyze
   * @return inferred parameter nullability; {@link Nullability#UNKNOWN} if cannot be inferred or non-applicable
   */
  public static Nullability inferNullability(@NotNull PsiParameter parameter) {
    if (parameter.getType() instanceof PsiPrimitiveType) return Nullability.UNKNOWN;
    PsiParameterList parent = ObjectUtils.tryCast(parameter.getParent(), PsiParameterList.class);
    if (parent == null) return Nullability.UNKNOWN;
    PsiMethodImpl method = ObjectUtils.tryCast(parent.getParent(), PsiMethodImpl.class);
    if (method == null) return Nullability.UNKNOWN;

    BitSet notNullParameters = getInferenceData(method).notNullParameters;
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
    return getInferenceData(method).mutability;
  }

  /**
   * Infer method contracts
   *
   * @param method method to analyze
   * @return inferred contracts; empty list of cannot be inferred or non-applicable
   */
  @NotNull
  public static List<StandardMethodContract> inferContracts(@NotNull PsiMethodImpl method) {
    return getInferenceData(method).contracts;
  }

  /**
   * Infer method mutation signature
   *
   * @param method method to analyze
   * @return method mutation signature; {@link MutationSignature#unknown()} if cannot be inferred
   */
  public static MutationSignature inferMutationSignature(@NotNull PsiMethodImpl method) {
    return getInferenceData(method).signature;
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

  public static boolean canInferFromSource(@NotNull PsiMethodImpl method) {
    return getInferenceMode(method) == InferenceMode.ENABLED;
  }

  private static InferenceMode getInferenceMode(@NotNull PsiMethodImpl method) {
    if (isLibraryCode(method) ||
        method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        method.hasModifierProperty(PsiModifier.NATIVE)) {
      return InferenceMode.DISABLED;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC)) return InferenceMode.ENABLED;
    if (PsiUtil.canBeOverridden(method)) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && (PsiUtil.isLocalClass(containingClass) ||
                                      !containingClass.isInterface() && containingClass.hasModifierProperty(PsiModifier.PRIVATE))) {
        if (ClassInheritorsSearch.search(containingClass, new LocalSearchScope(containingClass.getContainingFile()), false)
              .findFirst() == null) {
          return InferenceMode.ENABLED;
        }
      }
      return InferenceMode.PARAMETERS;
    }
    if (isUnusedInAnonymousClass(method)) return InferenceMode.DISABLED;

    return InferenceMode.ENABLED;
  }

  private static boolean isUnusedInAnonymousClass(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (!(containingClass instanceof PsiAnonymousClass)) {
      return false;
    }

    PsiElement parent = containingClass.getParent();
    if (parent instanceof PsiNewExpression &&
        PsiUtil.skipParenthesizedExprUp(parent.getParent()) instanceof PsiVariable &&
        !method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) {
      // references outside anonymous class can still resolve to this method, see com.intellij.psi.scope.util.PsiScopesUtil.setupAndRunProcessor()
      return false;
    }

    return SyntaxTraverser.psiTraverser(containingClass).filter(PsiReferenceExpression.class)
      .find(methodRef -> methodRef.isReferenceTo(method)) == null;
  }

  private static boolean isLibraryCode(@NotNull PsiMethod method) {
    if (method instanceof PsiCompiledElement) return true;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(method);
    return virtualFile != null && FileIndexFacade.getInstance(method.getProject()).isInLibrarySource(virtualFile);
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.*;
import com.intellij.java.library.JavaLibraryModificationTracker;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Methods to operate on Java contracts
 */
public final class JavaMethodContractUtil {
  private JavaMethodContractUtil() {}

  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();

  /**
   * Returns a list of contracts defined for given method call (including hardcoded contracts if any)
   *
   * @param call a method call site.
   * @return list of contracts (empty list if no contracts found)
   */
  public static @NotNull List<? extends MethodContract> getMethodCallContracts(@NotNull PsiCallExpression call) {
    PsiMethod method = call.resolveMethod();
    return method == null ? Collections.emptyList() : getMethodCallContracts(method, call);
  }

  /**
   * Returns a list of contracts defined for given method call (including hardcoded contracts if any)
   *
   * @param method a method to check the contracts for
   * @param call an optional call site. If specified, could be taken into account to derive contracts for some
   *             testing methods like assertThat(x, is(null))
   * @return list of contracts (empty list if no contracts found)
   */
  public static @NotNull List<? extends MethodContract> getMethodCallContracts(@NotNull PsiMethod method,
                                                                               @Nullable PsiCallExpression call) {
    List<MethodContract> hardcoded =
      HardcodedContracts.getHardcodedContracts(method, ObjectUtils.tryCast(call, PsiMethodCallExpression.class));
    if (!hardcoded.isEmpty()) {
      NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(method.getProject()).findEffectiveNullabilityInfo(method);
      if (info == null || info.isExternal() || info.getNullability() != Nullability.NOT_NULL) {
        return hardcoded;
      }
      if (HardcodedContracts.getHardcodedContracts(method, null).isEmpty()) {
        // Contract is derived for the call (like AssertJ assertion) -- preserve it
        return hardcoded;
      }
    }
    return getMethodContracts(method);
  }

  /**
   * Returns a list of contracts defined for given method call (excluding hardcoded contracts)
   *
   * @param method a method to check the contracts for
   * @return list of contracts (empty list if no contracts found)
   */
  public static @NotNull List<StandardMethodContract> getMethodContracts(@NotNull PsiMethod method) {
    return getContractInfo(method).getContracts();
  }

  /**
   * Checks whether method has an explicit contract annotation (either in source code or as external annotation)
   *
   * @param method method to check
   * @return true if method has explicit (non-inferred) contract annotation.
   */
  public static boolean hasExplicitContractAnnotation(@NotNull PsiMethod method) {
    return getContractInfo(method).isExplicit();
  }

  /**
   * Creates a new {@link PsiAnnotation} describing the updated contract. Only contract clauses are updated;
   * purity and mutation signature (if exist) are left as is.
   *
   * @param annotation original annotation to update
   * @param contracts new contracts
   * @return new {@link PsiAnnotation} object which describes updated contracts or null if no annotation is required to represent
   * the target contracts (i.e. contracts is empty, method has no mutation signature and is not marked as pure).
   */
  public static @Nullable PsiAnnotation updateContract(PsiAnnotation annotation, List<StandardMethodContract> contracts) {
    boolean pure = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "pure"));
    String mutates = StringUtil.notNullize(AnnotationUtil.getStringAttributeValue(annotation, MutationSignature.ATTR_MUTATES));
    String resultValue = StreamEx.of(contracts).joining("; ");
    String attributes = createAttributesText(resultValue, pure, mutates);
    if (attributes.isEmpty()) return null;
    return JavaPsiFacade.getElementFactory(annotation.getProject())
      .createAnnotationFromText("@" + annotation.getQualifiedName() + "(" + attributes + ")", annotation);
  }

  /**
   * Creates a string representing attributes for a method's contract annotation based on provided parameters.
   *
   * @param contracts a string describing the method's contracts using the contract syntax
   * @param pure indicates whether the method is pure (does not modify any state)
   * @param mutates specifier which describes which method parameters can be mutated during the method cal, if pure is passed as true, this
   *                argument is ignored.
   * @return contract arguments as a string, for example <code>value = "null, _ -> false", pure = true</code>
   */
  public static @NotNull String createAttributesText(String contracts, boolean pure, String mutates) {
    @NonNls Map<String, String> attrMap = new LinkedHashMap<>();
    if (!contracts.isEmpty()) {
      attrMap.put("value", StringUtil.wrapWithDoubleQuote(contracts));
    }
    if (pure) {
      attrMap.put("pure", "true");
    }
    else if (!mutates.trim().isEmpty()) {
      attrMap.put("mutates", StringUtil.wrapWithDoubleQuote(mutates));
    }
    if (attrMap.isEmpty()) {
      return "";
    }
    return attrMap.keySet().equals(Collections.singleton("value")) ?
           attrMap.get("value") : EntryStream.of(attrMap).join(" = ").joining(", ");
  }

  static class ContractInfo {
    static final ContractInfo EMPTY = new ContractInfo(Collections.emptyList(), false, false, MutationSignature.UNKNOWN);
    static final ContractInfo PURE = new ContractInfo(Collections.emptyList(), true, false, MutationSignature.transparent());

    private final @NotNull List<StandardMethodContract> myContracts;
    private final boolean myPure;
    private final boolean myExplicit;
    private final @NotNull MutationSignature myMutationSignature;

    ContractInfo(@NotNull List<StandardMethodContract> contracts, boolean pure, boolean explicit, @NotNull MutationSignature signature) {
      myContracts = contracts;
      myPure = pure;
      myExplicit = explicit;
      myMutationSignature = signature;
    }

    @NotNull
    List<StandardMethodContract> getContracts() {
      return myContracts;
    }

    boolean isPure() {
      return myPure;
    }

    boolean isExplicit() {
      return myExplicit;
    }

    @NotNull
    MutationSignature getMutationSignature() {
      return myMutationSignature;
    }
  }

  static @NotNull ContractInfo getContractInfo(@NotNull PsiMethod method) {
    if (PsiUtil.isAnnotationMethod(method) || method instanceof LightRecordMethod) {
      return ContractInfo.PURE;
    }
    return CachedValuesManager.getCachedValue(method, () -> {
      PsiAnnotation contractAnno = findContractAnnotation(method);
      ContractInfo info = ContractInfo.EMPTY;
      if (contractAnno != null) {
        List<StandardMethodContract> contracts = parseContracts(method, contractAnno);
        boolean pure = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(contractAnno, "pure"));
        MutationSignature mutationSignature = MutationSignature.UNKNOWN;
        if (pure) {
          mutationSignature = MutationSignature.pure();
        } else {
          String mutationText = AnnotationUtil.getStringAttributeValue(contractAnno, MutationSignature.ATTR_MUTATES);
          if (mutationText != null) {
            try {
              mutationSignature = MutationSignature.parse(mutationText);
            }
            catch (IllegalArgumentException ignored) {
            }
          }
        }
        boolean explicit = !AnnotationUtil.isInferredAnnotation(contractAnno);
        info = new ContractInfo(contracts, pure, explicit, mutationSignature);
      }

      PsiFile file = method.getContainingFile();
      if (file != null
          && file.getVirtualFile() != null
          && ProjectFileIndex.getInstance(method.getProject()).isInLibrary(file.getVirtualFile())) {
        // there is no need to recompute info on changes in the project code
        return Result.create(info, JavaLibraryModificationTracker.getInstance(method.getProject()));
      }

      return Result.create(info, method, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  /**
   * Parse contracts for given method. Calling this method is rarely necessary in client code; it exists mainly to
   * aid the inference procedure. Use {@link #getMethodContracts(PsiMethod)} instead.
   *
   * @param method method to parse contracts for
   * @param contractAnno a contract annotation
   * @return a list of parsed contracts
   */
  public static @NotNull List<StandardMethodContract> parseContracts(@NotNull PsiMethod method, @Nullable PsiAnnotation contractAnno) {
    if (contractAnno == null) return Collections.emptyList();
    String text = AnnotationUtil.getStringAttributeValue(contractAnno, null);
    if (text != null) {
      try {
        final int paramCount = method.getParameterList().getParametersCount();
        List<StandardMethodContract> parsed = StandardMethodContract.parseContract(text);
        if (ContainerUtil.and(parsed, c -> c.getParameterCount() == paramCount)) {
          return parsed;
        }
      }
      catch (StandardMethodContract.ParseException ignored) {
      }
    }
    return Collections.emptyList();
  }

  /**
   * Returns a contract annotation for a given method, checking the hierarchy
   *
   * @param method a method
   * @param skipExternal to skip external annotations
   * @return a found annotation (null if not found)
   */
  public static @Nullable PsiAnnotation findContractAnnotation(@NotNull PsiMethod method, boolean skipExternal) {
    return AnnotationUtil.findAnnotationInHierarchy(method,
                                                    Set.of(StaticAnalysisAnnotationManager.getInstance().getKnownContractAnnotations()),
                                                    skipExternal);
  }

  /**
   * Returns a contract annotation for a given method, checking the hierarchy
   *
   * @param method a method
   * @return a found annotation (null if not found)
   */
  public static @Nullable PsiAnnotation findContractAnnotation(@NotNull PsiMethod method) {
    return AnnotationUtil.findAnnotationInHierarchy(method,
                                                    Set.of(StaticAnalysisAnnotationManager.getInstance().getKnownContractAnnotations()),
                                                    false);
  }

  /**
   * Checks the method purity based on its contract
   *
   * @param method method to check
   * @return true if the method known to be pure (see {@link Contract#pure()} for details).
   */
  public static boolean isPure(@NotNull PsiMethod method) {
    return getContractInfo(method).isPure();
  }

  /**
   * Returns the common return value of the method assuming that it does not fail
   *
   * @param contracts method contracts
   * @return common return value or null if there's no common return value
   */
  public static @Nullable ContractReturnValue getNonFailingReturnValue(List<? extends MethodContract> contracts) {
    List<ContractValue> failConditions = new ArrayList<>();
    for (MethodContract contract : contracts) {
      List<ContractValue> conditions = contract.getConditions();
      if (conditions.isEmpty() || ContainerUtil.and(conditions, c -> failConditions.stream().anyMatch(c::isExclusive))) {
        return contract.getReturnValue();
      }
      if (contract.getReturnValue().isFail()) {
        // support "null, _ -> fail; !null, _ -> this", but do not support more complex cases like "null, true -> fail; !null, false -> this"
        if (conditions.size() == 1) {
          failConditions.add(conditions.get(0));
        }
      }
      else {
        break;
      }
    }
    return null;
  }

  /**
   * For given method call find the returned expression if the method is known to return always the same parameter or its qualifier
   * (unless fail).
   *
   * @param call call to analyze
   * @return the expression which is always returned by this method if it completes successfully,
   * null if method may return something less trivial or its contract is unknown.
   */
  @Contract("null -> null")
  public static @Nullable PsiExpression findReturnedValue(@Nullable PsiMethodCallExpression call) {
    if (call == null) return null;
    List<? extends MethodContract> contracts = getMethodCallContracts(call);
    ContractReturnValue returnValue = getNonFailingReturnValue(contracts);
    if (returnValue == null) return null;
    return returnValue.findPlace(call);
  }
}

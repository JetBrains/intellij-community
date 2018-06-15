// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Methods to operate on Java contracts
 */
public class JavaMethodContractUtil {
  private JavaMethodContractUtil() {}

  /**
   * JetBrains contract annotation fully-qualified name
   */
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();

  /**
   * Returns a list of contracts defined for given method call (including hardcoded contracts if any)
   *
   * @param call a method call site.
   * @return list of contracts (empty list if no contracts found)
   */
  @NotNull
  public static List<? extends MethodContract> getMethodCallContracts(@NotNull PsiCallExpression call) {
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
  @NotNull
  public static List<? extends MethodContract> getMethodCallContracts(@NotNull final PsiMethod method,
                                                                      @Nullable PsiCallExpression call) {
    List<MethodContract> contracts =
      HardcodedContracts.getHardcodedContracts(method, ObjectUtils.tryCast(call, PsiMethodCallExpression.class));
    return !contracts.isEmpty() ? contracts : getMethodContracts(method);
  }

  /**
   * Returns a list of contracts defined for given method call (excluding hardcoded contracts)
   *
   * @param method a method to check the contracts for
   * @return list of contracts (empty list if no contracts found)
   */
  @NotNull
  public static List<StandardMethodContract> getMethodContracts(@NotNull final PsiMethod method) {
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

  static class ContractInfo {
    static final ContractInfo EMPTY = new ContractInfo(Collections.emptyList(), false, false, MutationSignature.UNKNOWN);

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

  @NotNull
  static ContractInfo getContractInfo(@NotNull PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> {
      final PsiAnnotation contractAnno = findContractAnnotation(method);
      ContractInfo info = ContractInfo.EMPTY;
      if (contractAnno != null) {
        String text = AnnotationUtil.getStringAttributeValue(contractAnno, null);
        List<StandardMethodContract> contracts = Collections.emptyList();
        if (text != null) {
          try {
            final int paramCount = method.getParameterList().getParametersCount();
            List<StandardMethodContract> collection = StandardMethodContract.parseContract(text);
            if (collection.stream().allMatch(c -> c.getParameterCount() == paramCount)) {
              contracts = collection;
            }
          }
          catch (StandardMethodContract.ParseException ignored) {
          }
        }
        boolean pure = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(contractAnno, "pure"));
        MutationSignature mutationSignature = MutationSignature.UNKNOWN;
        if (pure) {
          mutationSignature = MutationSignature.PURE;
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
      return CachedValueProvider.Result.create(info, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  /**
   * Returns a contract annotation for given method, checking the hierarchy
   *
   * @param method a method
   * @return a found annotation (null if not found)
   */
  @Nullable
  public static PsiAnnotation findContractAnnotation(@NotNull PsiMethod method) {
    return AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(ORG_JETBRAINS_ANNOTATIONS_CONTRACT));
  }

  /**
   * Checks the method purity based on its contract
   *
   * @param method method to check
   * @return true if the method known to be pure (see {@link Contract#pure()} for details).
   */
  public static boolean isPure(@NotNull PsiMethod method) {
    return getContractInfo(method).myPure;
  }

  /**
   * Returns the common return value of the method assuming that it does not fail
   *
   * @param contracts method contracts
   * @return common return value or null if there's no common return value
   */
  @Nullable
  public static ContractReturnValue getNonFailingReturnValue(List<? extends MethodContract> contracts) {
    List<ContractValue> failConditions = new ArrayList<>();
    for (MethodContract contract : contracts) {
      List<ContractValue> conditions = contract.getConditions();
      if (conditions.isEmpty() || conditions.stream().allMatch(c -> failConditions.stream().anyMatch(c::isExclusive))) {
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
  @Nullable
  @Contract("null -> null")
  public static PsiExpression findReturnedValue(@Nullable PsiMethodCallExpression call) {
    if (call == null) return null;
    List<? extends MethodContract> contracts = getMethodCallContracts(call);
    ContractReturnValue returnValue = getNonFailingReturnValue(contracts);
    if (returnValue == null) return null;
    if (returnValue.equals(ContractReturnValue.returnThis())) {
      return ExpressionUtils.getQualifierOrThis(call.getMethodExpression());
    }
    if (returnValue instanceof ContractReturnValue.ParameterReturnValue) {
      int number = ((ContractReturnValue.ParameterReturnValue)returnValue).getParameterNumber();
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length <= number) return null;
      if (args.length == number + 1 && MethodCallUtils.isVarArgCall(call)) return null;
      return args[number];
    }
    return null;
  }
}

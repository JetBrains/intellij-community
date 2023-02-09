// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The extension helps to encapsulate a custom logic for "Safe delete" refactoring.
 */
public interface JavaSafeDeleteDelegate {
  LanguageExtension<JavaSafeDeleteDelegate> EP =
    new LanguageExtension<>("com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate");

  /**
   * Method is used to create usage information according to the input {@code reference} to the {@code parameter}.
   * <p/>
   * The result will be filled into the list of the usages.
   * <p> The method should be called under read action.
   *     A caller should be also aware that an implementation may use an index access,
   *     so using the method in EDT may lead to get the exception from {@link SlowOperations#assertSlowOperationsAreAllowed()}
   * @param paramIdx index with receiver parameter
   */
  void createUsageInfoForParameter(@NotNull PsiReference reference,
                                   @NotNull List<? super UsageInfo> usages,
                                   @NotNull PsiNamedElement parameter,
                                   int paramIdx,
                                   boolean isVararg);
  /**
   * Method is used to create usage information for type parameter.
   * <p/>
   * <p> The method should be called under read action.
   *     A caller should be also aware that an implementation may use an index access,
   *     so using the method in EDT may lead to get the exception from {@link SlowOperations#assertSlowOperationsAreAllowed()}
  */
  void createJavaTypeParameterUsageInfo(@NotNull PsiReference reference, 
                                        @NotNull List<? super UsageInfo> usages,
                                        @NotNull PsiElement typeParameter,
                                        int paramsCount,
                                        int index);

  /**
   * Method is used to create usage to remove {@code @Override} annotation in java or corresponding {@code override} modifier in kotlin
   *
   * @param overriddenFunction method which overrides method to be deleted
   * @param elements2Delete all elements which would be deleted this time
   */
  void createCleanupOverriding(@NotNull PsiElement overriddenFunction, PsiElement @NotNull [] elements2Delete, @NotNull List<? super UsageInfo> result);

  UsageInfo createExtendsListUsageInfo(PsiElement refElement,
                                       PsiReference reference);
}

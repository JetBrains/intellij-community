// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public abstract class TestFrameworks {
  public static TestFrameworks getInstance() {
    return ApplicationManager.getApplication().getService(TestFrameworks.class);
  }

  public abstract boolean isTestClass(@NotNull PsiClass psiClass);
  public abstract boolean isPotentialTestClass(@NotNull PsiClass psiClass);

  @Nullable
  public abstract PsiMethod findOrCreateSetUpMethod(PsiClass psiClass);

  @Nullable
  public abstract PsiMethod findSetUpMethod(PsiClass psiClass);

  @Nullable
  public abstract PsiMethod findTearDownMethod(PsiClass psiClass);

  protected abstract boolean hasConfigMethods(PsiClass psiClass);

  public abstract boolean isTestMethod(PsiMethod method);

  /**
   * Checks method on the possibility to run as a test
   *
   * @param method        method element to check
   * @param checkAbstract the fact that an abstract class is a test or not, if false then is test
   * @return the result of checking
   */
  public boolean isTestMethod(PsiMethod method, boolean checkAbstract) {
    return isTestMethod(method);
  }

  public boolean isTestOrConfig(PsiClass psiClass) {
    return isTestClass(psiClass) || hasConfigMethods(psiClass);
  }

  @Nullable
  public static TestFramework detectFramework(@NotNull final PsiClass psiClass) {
    return ContainerUtil.getFirstItem(detectApplicableFrameworks(psiClass));
  }

  @NotNull
  public static Set<TestFramework> detectApplicableFrameworks(@NotNull final PsiClass psiClass) {
    PsiModifierListOwner normalized = AnnotationCacheOwnerNormalizer.normalize(psiClass);
    return CachedValuesManager.getCachedValue(normalized, () -> CachedValueProvider.Result
      .create(computeFrameworks(normalized), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static Set<TestFramework> computeFrameworks(PsiElement psiClass) {
    Set<TestFramework> frameworks = new LinkedHashSet<>();
    for (TestFramework framework : TestFramework.EXTENSION_NAME.getExtensionList()) {
      if (framework.isTestClass(psiClass)) {
        frameworks.add(framework);
      }
    }

    for (TestFramework framework : TestFramework.EXTENSION_NAME.getExtensionList()) {
      if (frameworks.contains(framework)) continue;
      if (framework.findSetUpMethod(psiClass) != null || framework.findTearDownMethod(psiClass) != null) {
        frameworks.add(framework);
      }
    }
    return frameworks;
  }
}

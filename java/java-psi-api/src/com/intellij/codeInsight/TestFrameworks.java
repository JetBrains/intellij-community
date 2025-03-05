// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public abstract class TestFrameworks {
  public static TestFrameworks getInstance() {
    return ApplicationManager.getApplication().getService(TestFrameworks.class);
  }

  public abstract boolean isTestClass(@NotNull PsiClass psiClass);
  public abstract boolean isPotentialTestClass(@NotNull PsiClass psiClass);

  public abstract @Nullable PsiMethod findOrCreateSetUpMethod(PsiClass psiClass);

  public abstract @Nullable PsiMethod findSetUpMethod(PsiClass psiClass);

  public abstract @Nullable PsiMethod findTearDownMethod(PsiClass psiClass);

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

  public static @Nullable TestFramework detectFramework(final @NotNull PsiClass psiClass) {
    return ContainerUtil.getFirstItem(detectApplicableFrameworks(psiClass));
  }

  public static @NotNull Set<TestFramework> detectApplicableFrameworks(final @NotNull PsiClass psiClass) {
    PsiModifierListOwner normalized = AnnotationCacheOwnerNormalizer.normalize(psiClass);
    return CachedValuesManager.getCachedValue(normalized, () -> CachedValueProvider.Result
      .create(computeFrameworks(normalized), PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static Set<TestFramework> computeFrameworks(PsiElement psiClass) {
    Set<TestFramework> frameworks = new LinkedHashSet<>();

    Language classLanguage = psiClass.getLanguage();
    Map<String, Language> checkedFrameworksByName = new HashMap<>();

    for (TestFramework framework : DumbService.getDumbAwareExtensions(psiClass.getProject(), TestFramework.EXTENSION_NAME)) {
      String frameworkName = framework.getName();
      Language frameworkLanguage = framework.getLanguage();

      Language checkedFrameworkLanguage = checkedFrameworksByName.get(frameworkName);
      // if we've checked framework for more specific language - no reasons to check it again for more general language
      if (checkedFrameworkLanguage != null && isSubLanguage(checkedFrameworkLanguage, frameworkLanguage)) continue;

      if (!isSubLanguage(classLanguage, frameworkLanguage))
        continue;

      if (framework.isTestClass(psiClass) ||
          framework.findSetUpMethod(psiClass) != null ||
          framework.findTearDownMethod(psiClass) != null) {
        frameworks.add(framework);
      }
      checkedFrameworksByName.put(frameworkName, frameworkLanguage);
    }
    return frameworks;
  }

  /**
   * @return <code>true</code> if <code>framework</code> could handle element by its language
   */
  public static boolean isSuitableByLanguage(PsiElement element, TestFramework framework) {
    return element.getContainingFile() != null && isSubLanguage(element.getLanguage(), framework.getLanguage());
  }

  private static boolean isSubLanguage(@NotNull Language language, @NotNull Language parentLanguage) {
    return parentLanguage == Language.ANY || language.isKindOf(parentLanguage);
  }
}

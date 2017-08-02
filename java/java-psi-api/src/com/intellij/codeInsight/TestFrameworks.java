/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author yole
 */
public abstract class TestFrameworks {
  public static TestFrameworks getInstance() {
    return ServiceManager.getService(TestFrameworks.class);
  }

  public abstract boolean isTestClass(PsiClass psiClass);
  public abstract boolean isPotentialTestClass(PsiClass psiClass);

  @Nullable
  public abstract PsiMethod findOrCreateSetUpMethod(PsiClass psiClass);

  @Nullable
  public abstract PsiMethod findSetUpMethod(PsiClass psiClass);

  @Nullable
  public abstract PsiMethod findTearDownMethod(PsiClass psiClass);

  protected abstract boolean hasConfigMethods(PsiClass psiClass);
  
  public abstract boolean isTestMethod(PsiMethod method);

  public boolean isTestOrConfig(PsiClass psiClass) {
    return isTestClass(psiClass) || hasConfigMethods(psiClass);
  }
  
  @Nullable
  public static TestFramework detectFramework(@NotNull final PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> CachedValueProvider.Result
      .create(computeFramework(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  @NotNull
  public static Set<TestFramework> detectApplicableFrameworks(@NotNull final PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> CachedValueProvider.Result
      .create(computeFrameworks(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  private static Set<TestFramework> computeFrameworks(PsiClass psiClass) {
    Set<TestFramework> frameworks = new LinkedHashSet<>();
    for (TestFramework framework : Extensions.getExtensions(TestFramework.EXTENSION_NAME)) {
      if (framework.isTestClass(psiClass)) {
        frameworks.add(framework);
      }
    }

    for (TestFramework framework : Extensions.getExtensions(TestFramework.EXTENSION_NAME)) {
      if (frameworks.contains(framework)) continue;;
      if (framework.findSetUpMethod(psiClass) != null || framework.findTearDownMethod(psiClass) != null) {
        frameworks.add(framework);
      }
    }
    return frameworks;
  }

  @Nullable
  private static TestFramework computeFramework(PsiClass psiClass) {
    for (TestFramework framework : Extensions.getExtensions(TestFramework.EXTENSION_NAME)) {
      if (framework.isTestClass(psiClass)) {
        return framework;
      }
    }

    for (TestFramework framework : Extensions.getExtensions(TestFramework.EXTENSION_NAME)) {
      if (framework.findSetUpMethod(psiClass) != null || framework.findTearDownMethod(psiClass) != null) {
        return framework;
      }
    }
    return null;
  }
}

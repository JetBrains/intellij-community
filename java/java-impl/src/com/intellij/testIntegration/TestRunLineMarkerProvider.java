// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class TestRunLineMarkerProvider extends RunLineMarkerContributor {
  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement e) {
    if (isIdentifier(e)) {
      PsiElement element = e.getParent();
      if (element instanceof PsiClass psiClass) {
        if (!isTestClass(psiClass)) return null;
        String url = "java:suite://" + ClassUtil.getJVMClassName(psiClass);
        TestStateStorage.Record state = TestStateStorage.getInstance(e.getProject()).getState(url);
        return getInfo(state, true, PsiMethodUtil.findMainInClass(psiClass) != null ? 1 : 0);
      }
      if (element instanceof PsiMethod psiMethod) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);
        if (!isTestMethod(containingClass, psiMethod)) return null;
        if (isIgnoredForGradleConfiguration(containingClass, psiMethod)) return null;
        String url = "java:test://" + ClassUtil.getJVMClassName(containingClass) + "/" + psiMethod.getName();
        TestStateStorage.Record state = TestStateStorage.getInstance(e.getProject()).getState(url);
        return getInfo(state, false, 0);
      }
    }
    return null;
  }

  private static boolean isIgnoredForGradleConfiguration(@Nullable PsiClass psiClass, @Nullable PsiMethod psiMethod) {
    if (psiClass == null || psiMethod == null) return false;
    RunnerAndConfigurationSettings currentConfiguration = RunManager.getInstance(psiClass.getProject()).getSelectedConfiguration();
    if (currentConfiguration == null) return false;
    ConfigurationType configurationType = currentConfiguration.getType();
    if (!configurationType.getId().equals("GradleRunConfiguration")) return false;
    for (TestFramework testFramework : TestFramework.EXTENSION_NAME.getExtensionList()) {
      if (testFramework.isTestClass(psiClass) && testFramework.isIgnoredMethod(psiMethod)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTestClass(PsiClass clazz) {
    TestFramework framework = TestFrameworks.detectFramework(clazz);
    return framework != null && framework.isTestClass(clazz);
  }

  private static boolean isTestMethod(PsiClass containingClass, PsiMethod method) {
    if (containingClass == null) return false;
    TestFramework framework = TestFrameworks.detectFramework(containingClass);
    return framework != null && framework.isTestMethod(method, false);
  }

  @NotNull
  private static Info getInfo(TestStateStorage.Record state, boolean isClass, int order) {
    return new Info(getTestStateIcon(state, isClass), ExecutorAction.getActions(order), element -> ExecutionBundle.message("run.text"));
  }

  protected boolean isIdentifier(PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}

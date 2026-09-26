// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class TestRunLineMarkerProvider extends RunLineMarkerContributor implements DumbAware {
  private static final String URL_TEST_PREFIX = "java:test://";
  private static final String URL_SUITE_PREFIX = "java:suite://";
  private static final Logger LOG = Logger.getInstance(TestRunLineMarkerProvider.class);


  @Override
  public @Nullable Info getInfo(@NotNull PsiElement e) {
    if (isIdentifier(e)) {
      PsiElement element = e.getParent();
      if (element instanceof PsiClass psiClass) {
        if (!isTestClass(psiClass)) return null;
        String url = URL_SUITE_PREFIX + ClassUtil.getJVMClassName(psiClass);
        TestStateStorage.Record state = TestStateStorage.getInstance(e.getProject()).getState(url);
        return getInfo(state, true, PsiMethodUtil.hasMainInClass(psiClass) ? 1 : 0);
      }
      if (element instanceof PsiMethod psiMethod) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);
        if (!isTestMethod(containingClass, psiMethod)) return null;
        if (isIgnoredForGradleConfiguration(containingClass, psiMethod)) return null;
        String urlSuffix = ClassUtil.getJVMClassName(containingClass) + "/" + psiMethod.getName();

        List<String> urlList = new ArrayList<>();
        urlList.add(URL_TEST_PREFIX + urlSuffix);
        if (isGradleConfiguration(containingClass)) {
          urlList.add(URL_SUITE_PREFIX + urlSuffix);
        }

        TestStateStorage.Record state = null;
        for (String url : urlList) {
          state = TestStateStorage.getInstance(e.getProject()).getState(url);
          if (state != null) break;
        }
        return getInfo(state, false, 0);
      }
    }
    return null;
  }

  /**
   * Gradle can't run ignored methods while IDEA runner can, so when using a Gradle configuration, we don't want to show the line marker for
   * ignored methods.
   */
  private static boolean isIgnoredForGradleConfiguration(@NotNull PsiClass psiClass, @NotNull PsiMethod psiMethod) {
    if (!isGradleConfiguration(psiClass)) return false;
    //now gradle doesn't support dumb mode
    if (DumbService.getInstance(psiClass.getProject()).isDumb()) return true;
    for (TestFramework testFramework : TestFramework.EXTENSION_NAME.getExtensionList()) {
      if (testFramework.isTestClass(psiClass) && testFramework.isIgnoredMethod(psiMethod)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isGradleConfiguration(@NotNull PsiClass psiClass) {
    RunnerAndConfigurationSettings currentConfiguration = RunManager.getInstance(psiClass.getProject()).getSelectedConfiguration();
    if (currentConfiguration == null) return false;
    ConfigurationType configurationType = currentConfiguration.getType();
    return configurationType.getId().equals("GradleRunConfiguration");
  }

  private static boolean isTestClass(PsiClass clazz) {
    if (clazz == null) return false;
    try {
      return DumbService.getInstance(clazz.getProject()).computeWithAlternativeResolveEnabled(() -> {
        TestFramework framework = TestFrameworks.detectFramework(clazz);
        return framework != null && framework.isTestClass(clazz);
      });
    }
    catch (IndexNotReadyException e) {
      LOG.error(e);
      return false;
    }
  }

  private static boolean isTestMethod(PsiClass containingClass, PsiMethod method) {
    if (containingClass == null) return false;
    TestFramework framework = TestFrameworks.detectFramework(containingClass);
    return framework != null && framework.isTestMethod(method, false);
  }

  private static @NotNull Info getInfo(TestStateStorage.Record state, boolean isClass, int order) {
    AnAction[] actions = ExecutorAction.getActions(order);
    return new Info(getTestStateIcon(state, isClass), actions, element -> ExecutionBundle.message("run.text"));
  }

  protected boolean isIdentifier(PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.TestStateStorage;
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
      if (element instanceof PsiClass) {
        if (!isTestClass((PsiClass)element)) return null;
        String url = "java:suite://" + ClassUtil.getJVMClassName((PsiClass)element);
        TestStateStorage.Record state = TestStateStorage.getInstance(e.getProject()).getState(url);
        return getInfo(state, true, PsiMethodUtil.findMainInClass((PsiClass)element) != null ? 1 : 0);
      }
      if (element instanceof PsiMethod) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (!isTestMethod(containingClass, (PsiMethod)element)) return null;
        String url = "java:test://" + ClassUtil.getJVMClassName(containingClass) + "/" + ((PsiMethod)element).getName();
        TestStateStorage.Record state = TestStateStorage.getInstance(e.getProject()).getState(url);
        return getInfo(state, false, 0);
      }
    }
    return null;
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

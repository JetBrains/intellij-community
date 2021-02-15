// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
        TestFramework framework = TestFrameworks.detectFramework((PsiClass)element);
        if (framework != null && framework.isTestClass(element)) {
          String url = "java:suite://" + ClassUtil.getJVMClassName((PsiClass)element);
          TestStateStorage.Record state = TestStateStorage.getInstance(e.getProject()).getState(url);
          return getInfo(state, true);
        }
      }
      if (element instanceof PsiMethod) {
        TestFailedLineManager.TestInfo testInfo = TestFailedLineManager.getInstance(e.getProject()).getTestInfo((PsiMethod)element);
        return testInfo == null ? null : getInfo(testInfo.myRecord, false);
      }
    }
    return null;
  }

  @NotNull
  private static Info getInfo(TestStateStorage.Record state, boolean isClass) {
    return new Info(getTestStateIcon(state, isClass), ExecutorAction.getActions(1), element -> ExecutionBundle.message("run.text"));
  }

  protected boolean isIdentifier(PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}

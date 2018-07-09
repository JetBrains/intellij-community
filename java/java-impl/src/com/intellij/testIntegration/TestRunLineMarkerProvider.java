/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
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

import javax.swing.*;

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
    Icon icon = getTestStateIcon(state, isClass);
    return new Info(icon, ExecutorAction.getActions(1), RunLineMarkerContributor.RUN_TEST_TOOLTIP_PROVIDER);
  }

  protected boolean isIdentifier(PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class TestRunLineMarkerProvider extends RunLineMarkerContributor {

  private static final Function<PsiElement, String> TOOLTIP_PROVIDER = new Function<PsiElement, String>() {
    @Override
    public String fun(PsiElement element) {
      return "Run Test";
    }
  };

  @Nullable
  @Override
  public Info getInfo(PsiElement e) {
    if (isIdentifier(e)) {
      PsiElement element = e.getParent();
      if (element instanceof PsiClass) {
        TestFramework framework = TestFrameworks.detectFramework((PsiClass)element);
        if (framework != null && framework.isTestClass(element)) {
          String url = "java:suite://" + ((PsiClass)element).getQualifiedName();
          return getInfo(url, e.getProject(), true);
        }
      }
      if (element instanceof PsiMethod) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass != null) {
          TestFramework framework = TestFrameworks.detectFramework(psiClass);
          if (framework != null && framework.isTestMethod(element)) {
            String url = "java:test://" + psiClass.getQualifiedName() + "." + ((PsiMethod)element).getName();
            return getInfo(url, e.getProject(), false);
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static Info getInfo(String url, Project project, boolean isClass) {
    Icon icon = getTestStateIcon(url, project, isClass);
    return new Info(icon, TOOLTIP_PROVIDER, ExecutorAction.getActions(1));
  }

  protected boolean isIdentifier(PsiElement e) {
    return e instanceof PsiIdentifier;
  }

  private static Icon getTestStateIcon(String url, Project project, boolean isClass) {
    TestStateStorage.Record state = TestStateStorage.getInstance(project).getState(url);
    if (state != null) {
      TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(state.magnitude);
      if (magnitude != null) {
        switch (magnitude) {
          case ERROR_INDEX:
          case FAILED_INDEX:
            return AllIcons.RunConfigurations.TestState.Red2;
          case PASSED_INDEX:
          case COMPLETE_INDEX:
            return AllIcons.RunConfigurations.TestState.Green2;
          default:
        }
      }
    }
    return isClass ? AllIcons.RunConfigurations.TestState.Run_run : AllIcons.RunConfigurations.TestState.Run;
  }
}

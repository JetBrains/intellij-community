// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

public interface TestFailedLineManager {
  static TestFailedLineManager getInstance(Project project) {
   return project.getService(TestFailedLineManager.class);
  }

  TestInfo getTestInfo(PsiElement element);

  LocalQuickFix getRunQuickFix(PsiElement element);

  LocalQuickFix getDebugQuickFix(PsiElement element, String topStackTraceLine);

  interface TestInfo {
    int getMagnitude();

    String getErrorMessage();

    String getTopStackTraceLine();
  }
}

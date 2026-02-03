// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class FindMethodDuplicatesBaseTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected void doTest() {
    doTest(true);
  }

  protected void doTest(final boolean shouldSucceed) {
    final String filePath = getTestFilePath();
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMember);
    final PsiMember psiMethod = (PsiMember)targetElement;

    assertSame(TestDialog.DEFAULT, TestDialogManager.getTestImplementation());
    try {
      MethodDuplicatesHandler.invokeOnScope(getProject(), psiMethod, new AnalysisScope(getFile()));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
    catch (RuntimeException e) {
      if (shouldSucceed) {
        fail("duplicates were not found");
      }
      return;
    }
    if (shouldSucceed) {
      checkResultByFile(filePath + ".after");
    }
    else {
      fail("duplicates found" + "; file content: " + getFile().getText());
    }
  }

  protected abstract String getTestFilePath();

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }
}
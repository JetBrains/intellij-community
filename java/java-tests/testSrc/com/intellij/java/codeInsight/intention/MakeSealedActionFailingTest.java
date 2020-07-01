// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.MakeSealedAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MakeSealedActionFailingTest extends LightJavaCodeInsightTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  public void testFunctionalInterface() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.is.used.in.functional.expression"));
  }

  public void testAnonymousClass() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.has.anonymous.inheritors"));
  }

  private void checkErrorMessage(@NotNull String message) {
    configureByFile("/codeInsight/daemonCodeAnalyzer/quickFix/makeClassSealed/failing/" + getTestName(false) + ".java");
    MakeSealedAction action = new MakeSealedAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    try {
      ApplicationManager.getApplication().runWriteAction(() -> action.invoke(getProject(), getEditor(), getFile()));
    } catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(message, e.getMessage());
      return;
    }
    fail("Test must fail with error message");
  }
}

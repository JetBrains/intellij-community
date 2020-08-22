// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.impl.SealClassAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class SealClassActionFailingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  public void testFunctionalInterface() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.is.used.in.functional.expression"));
  }

  public void testAnonymousClass() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.has.anonymous.or.local.inheritors"));
  }

  public void testLocalClass() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.has.anonymous.or.local.inheritors"));
  }

  public void testInterfaceWithoutInheritors() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.interface.has.no.inheritors"));
  }

  public void testDifferentPackages() {
    myFixture.addFileToProject("foo.java", "package other;\n class Other extends Parent {}");
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.different.packages"));
  }

  private void checkErrorMessage(@NotNull String message) {
    myFixture.configureByFile(getTestName(false) + ".java");
    SealClassAction action = new SealClassAction();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    try {
      ApplicationManager.getApplication().runWriteAction(() -> action.invoke(getProject(), getEditor(), getFile()));
    } catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(message, e.getMessage());
      return;
    }
    fail("Test must fail with error message");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/sealClass/failing/";
  }
}

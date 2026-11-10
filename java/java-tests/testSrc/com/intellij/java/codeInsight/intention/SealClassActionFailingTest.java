// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.impl.SealClassAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class SealClassActionFailingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/sealClass/failing/");
  }

  @Test
  public void testFunctionalInterface() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.is.used.in.functional.expression"));
  }

  @Test
  public void testAnonymousClass() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.has.anonymous.or.local.inheritors"));
  }

  @Test
  public void testLocalClass() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.has.anonymous.or.local.inheritors"));
  }

  @Test
  public void testInterfaceWithoutInheritors() {
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.interface.has.no.inheritors"));
  }

  @Test
  public void testDifferentPackages() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo.java", "package other;\n class Other extends Parent {}");
    checkErrorMessage(JavaBundle.message("intention.error.make.sealed.class.different.packages"));
  }

  private void checkErrorMessage(@NotNull String message) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      ActionContext ac = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
      SealClassAction action = new SealClassAction();
      assertNotNull(action.getPresentation(ac));
      ModCommand command = action.perform(ac);
      assertEquals(ModCommand.error(message), command);
    });
  }
}

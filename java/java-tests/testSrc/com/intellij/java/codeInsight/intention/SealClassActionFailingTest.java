// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.impl.SealClassAction;
import com.intellij.codeInspection.ModCommands;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction.ActionContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class SealClassActionFailingTest extends LightJavaCodeInsightFixtureTestCase {
  
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
    ActionContext ac = ActionContext.from(getEditor(), getFile());
    SealClassAction action = new SealClassAction();
    assertNotNull(action.getPresentation(ac));
    ModCommand command = action.perform(ac);
    assertEquals(ModCommands.error(message), command);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/sealClass/failing/";
  }
}

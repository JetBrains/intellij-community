// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.intention.impl.SplitIfAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class SplitIfActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void test1() {
    CodeStyle.getSettings(daemonFixture.get().getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE).ELSE_ON_NEW_LINE = true;
    doTest();
  }

  @Test
  public void test2() {
    doTest();
  }

  @Test
  public void test3() {
    doTest();
  }

  @Test
  public void test4() {
    doTest();
  }

  @Test
  public void test5() {
    doTest();
  }

  @Test
  public void testParenthesis() {
    doTest();
  }

  @Test
  public void testOrParenthesis() {
    doTest();
  }

  @Test
  public void testComment() {
    doTest();
  }

  @Test
  public void testCommentInside() { doTest(); }

  @Test
  public void testCommentBeforeElse() { doTest(); }

  @Test
  public void testCommentBeforeElse2() { doTest(); }

  @Test
  public void testChain() { doTest(); }

  @Test
  public void testChain2() { doTest(); }

  @Test
  public void testChain3() { doTest(); }

  @Test
  public void testChain4() { doTest(); }

  @Test
  public void testWithoutSpaces() {
    doTest();
  }

  @Test
  public void testRedundantChainedCondition() {
    doTest();
  }

  @Test
  public void testIncomplete() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile("/codeInsight/splitIfAction/before" + myFixture.getTestName(false) + ".java");
    SplitIfAction action = new SplitIfAction();
    ReadAction.runBlocking(() -> assertNull(action.getPresentation(ActionContext.from(myFixture.getEditor(), myFixture.getFile()))));
  }

  @Test
  public void testIncomplete2() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile("/codeInsight/splitIfAction/before" + myFixture.getTestName(false)+ ".java");
    CommonCodeStyleSettings settings =
      ReadAction.computeBlocking(() -> CodeStyle.getSettings(myFixture.getFile()).getCommonSettings(JavaLanguage.INSTANCE));
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    myFixture.runInCommand(this::perform);
    myFixture.checkResultByFile("/codeInsight/splitIfAction/after" + myFixture.getTestName(false) + ".java");
  }

   @Test
   public void test8() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile("/codeInsight/splitIfAction/beforeOrAndMixed.java");
    SplitIfAction action = new SplitIfAction();
     ReadAction.runBlocking(() -> assertNull(action.getPresentation(ActionContext.from(myFixture.getEditor(), myFixture.getFile()))));
   }


  private void perform() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    SplitIfAction action = new SplitIfAction();
    ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
    assertNotNull(action.getPresentation(context));
    ModCommand command = action.perform(context);
    ApplicationManager.getApplication().runWriteAction(
      () -> ModCommandExecutor.getInstance().executeInteractively(context, command, myFixture.getEditor()));
  }
}

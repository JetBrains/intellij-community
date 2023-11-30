// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.intention.impl.SplitIfAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

public class SplitIfActionTest extends LightJavaCodeInsightTestCase {
  public void test1() {
    CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).ELSE_ON_NEW_LINE = true;
    doTest();
  }

  public void test2() {
    doTest();
  }

  public void test3() {
    doTest();
  }

  public void test4() {
    doTest();
  }

  public void test5() {
    doTest();
  }

  public void testParenthesis() {
    doTest();
  }

  public void testOrParenthesis() {
    doTest();
  }

  public void testComment() {
    doTest();
  }

  public void testCommentInside() { doTest(); }

  public void testCommentBeforeElse() { doTest(); }

  public void testCommentBeforeElse2() { doTest(); }

  public void testChain() { doTest(); }

  public void testChain2() { doTest(); }

  public void testChain3() { doTest(); }

  public void testChain4() { doTest(); }

  public void testWithoutSpaces() {
    doTest();
  }

  public void testRedundantChainedCondition() {
    doTest();
  }

  public void testIncomplete() {
    configureByFile("/codeInsight/splitIfAction/before" + getTestName(false) + ".java");
    SplitIfAction action = new SplitIfAction();
    assertNull(action.getPresentation(ActionContext.from(getEditor(), getFile())));
  }

  public void testIncomplete2() {
    doTest();
  }

  private void doTest() {
    configureByFile("/codeInsight/splitIfAction/before" + getTestName(false)+ ".java");
    CommonCodeStyleSettings settings = CodeStyle.getSettings(getFile()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    perform();
    checkResultByFile("/codeInsight/splitIfAction/after" + getTestName(false) + ".java");
  }

   public void test8() {
    configureByFile("/codeInsight/splitIfAction/beforeOrAndMixed.java");
    SplitIfAction action = new SplitIfAction();
     assertNull(action.getPresentation(ActionContext.from(getEditor(), getFile())));
   }


  private void perform() {
    SplitIfAction action = new SplitIfAction();
    ActionContext context = ActionContext.from(getEditor(), getFile());
    assertNotNull(action.getPresentation(context));
    ModCommand command = action.perform(context);
    ApplicationManager.getApplication().runWriteAction(
      () -> ModCommandExecutor.getInstance().executeInteractively(context, command, getEditor()));
  }
}

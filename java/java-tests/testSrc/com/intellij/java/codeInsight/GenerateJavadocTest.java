// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

@HeavyPlatformTestCase.WrapInCommand
public class GenerateJavadocTest extends LightJavaCodeInsightTestCase {
  public void test1() { doTest(); }
  public void test2() { doTest(); }
  public void test3() { doTest(); }
  public void testIdeadev2328() { doTest(); }
  public void testIdeadev2328_2() { doTest(); }
  public void testBeforeCommentedJavadoc() { doTest(); }
  public void testDoubleSlashInJavadoc() { doTest(); }

  private void doTest() {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateJavadoc/before" + name + ".java");
    performAction();
    checkResultByFile("/codeInsight/generateJavadoc/after" + name + ".java");
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    DataContext context = DataManager.getInstance().getDataContext(getEditor().getComponent());
    actionHandler.execute(getEditor(), getEditor().getCaretModel().getCurrentCaret(), context);
  }
}

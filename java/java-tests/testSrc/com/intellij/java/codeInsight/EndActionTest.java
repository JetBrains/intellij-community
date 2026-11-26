// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class EndActionTest {

  private static final String PATH = "/codeInsight/endAction/";
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testNormal1() { doTest(); }
  @Test
  public void testNormal2() { doTest(); }

  @Test
  public void testEmptyLine1() {
    init(() -> assertEquals(new VisualPosition(1, 4), codeInsightFixture.get().getEditor().getCaretModel().getVisualPosition()));
  }

  @Test
  public void testInComment() { doTest(); }

  @Test
  public void testLongWhiteSpace() { doTest(); }

  @Test
  public void testAlignedJavadocParameterDescription() {
    init(() -> assertEquals(new VisualPosition(4, 23), codeInsightFixture.get().getEditor().getCaretModel().getVisualPosition()));
  }

  @Test
  public void testNonAlignedJavadocParameterDescription() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(PATH + name + ".java");
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).JD_ALIGN_PARAM_COMMENTS = false;
    myFixture.runInCommand(() -> {
      performAction();
      assertEquals(new VisualPosition(4, 16), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testNonEmptyParameterDescription() { doTest(); }

  @Test
  public void testNonEmptyMultiLineParameterDescription() { doTest(); }

  @Test
  public void testWhiteSpaceInsertionOnJavadocDescriptionPositionNavigation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(PATH + name + ".java");
    myFixture.runInCommand(() -> {
      myFixture.getEditor().getSettings().setVirtualSpace(false);
      performAction();
      myFixture.checkResultByFile(PATH + myFixture.getTestName(false) + ".java.after", false);
    });
  }

  @Test
  public void testAlignedReturnWithDescription() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(PATH + name + ".java");
    myFixture.runInCommand(() -> {
      myFixture.getEditor().getSettings().setVirtualSpace(false);
      performAction();
      myFixture.checkResultByFile(PATH + myFixture.getTestName(false) + ".java.after", false);
    });
  }

  @Test
  public void testWithSomeWhitespaceExisting() {
    doTest();
  }

  /**
   * Configures the file of the test, then runs the action and {@code check} in one command.
   */
  private void init(Runnable check) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(PATH + name + ".java");
    myFixture.runInCommand(() -> {
      performAction();
      check.run();
    });
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    init(() -> myFixture.checkResultByFile(PATH + myFixture.getTestName(false) + ".java.after", false));
  }

  private void performAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
    actionHandler.execute(myFixture.getEditor(), null, DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent()));
  }
}

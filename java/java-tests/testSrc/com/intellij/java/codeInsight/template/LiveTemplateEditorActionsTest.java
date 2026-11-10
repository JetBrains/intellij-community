// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class LiveTemplateEditorActionsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/editor/");
  }

  @AfterEach
  void tearDown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      TemplateState templateState = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      if (templateState != null) {
        templateState.gotoEnd(false);
      }
    });
  }

  @Test
  public void testHomeAction() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  @Test
  public void testHomeActionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  @Test
  public void testHomeActionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  @Test
  public void testHomeActionWithSelection() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  @Test
  public void testHomeActionWithSelectionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  @Test
  public void testHomeActionWithSelectionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  @Test
  public void testEndAction() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  @Test
  public void testEndActionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  @Test
  public void testEndActionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  @Test
  public void testEndActionWithSelection() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  @Test
  public void testEndActionWithSelectionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  @Test
  public void testEndActionWithSelectionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  public void doTest(@NotNull String actionId) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      expendTemplate();
      EditorModificationUtil.moveCaretRelatively(myFixture.getEditor(), -5);
      myFixture.performEditorAction(actionId);
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doTestWithSelection(@NotNull String actionId) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      expendTemplate();
      EditorModificationUtil.moveCaretRelatively(myFixture.getEditor(), -5);
      myFixture.getEditor().getSelectionModel().setSelection(myFixture.getCaretOffset(), myFixture.getCaretOffset() + 3);
      myFixture.performEditorAction(actionId);
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doTestOutsideSegment(@NotNull String actionId) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      expendTemplate();
      EditorModificationUtil.moveCaretRelatively(myFixture.getEditor(), 2);
      myFixture.performEditorAction(actionId);
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  private void expendTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByFile("BeforeTestData.java");

    final TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = manager.createTemplate("myTemplate", "test", "template $VAR$ text");
    template.addVariable("VAR", new EmptyNode(), true);
    TemplateManager.getInstance(myFixture.getProject()).startTemplate(myFixture.getEditor(), template);

    myFixture.type("myVariableValue");
  }
}
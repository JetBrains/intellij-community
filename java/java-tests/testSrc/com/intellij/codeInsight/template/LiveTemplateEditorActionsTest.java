/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LiveTemplateEditorActionsTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      TemplateState templateState = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      if (templateState != null) {
        templateState.gotoEnd(false);
      }
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testHomeAction() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  public void testHomeActionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  public void testHomeActionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  public void testHomeActionWithSelection() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  public void testHomeActionWithSelectionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  public void testHomeActionWithSelectionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION);
  }

  public void testEndAction() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  public void testEndActionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  public void testEndActionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  public void testEndActionWithSelection() {
    doTest(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  public void testEndActionWithSelectionWithExistingSelection() {
    doTestWithSelection(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  public void testEndActionWithSelectionOutsideVariableSegment() {
    doTestOutsideSegment(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION);
  }

  public void doTest(@NotNull String actionId) {
    expendTemplate();
    EditorModificationUtil.moveCaretRelatively(myFixture.getEditor(), -5);
    myFixture.performEditorAction(actionId);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTestWithSelection(@NotNull String actionId) {
    expendTemplate();
    EditorModificationUtil.moveCaretRelatively(myFixture.getEditor(), -5);
    myFixture.getEditor().getSelectionModel().setSelection(myFixture.getCaretOffset(), myFixture.getCaretOffset() + 3);
    myFixture.performEditorAction(actionId);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTestOutsideSegment(@NotNull String actionId) {
    expendTemplate();
    EditorModificationUtil.moveCaretRelatively(myFixture.getEditor(), 2);
    myFixture.performEditorAction(actionId);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void expendTemplate() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getProject(), getTestRootDisposable());
    myFixture.configureByFile("BeforeTestData.java");

    final TemplateManager manager = TemplateManager.getInstance(getProject());
    final Template template = manager.createTemplate("myTemplate", "test", "template $VAR$ text");
    template.addVariable("VAR", new EmptyNode(), true);
    TemplateManager.getInstance(myFixture.getProject()).startTemplate(myFixture.getEditor(), template);

    myFixture.type("myVariableValue");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/editor/";
  }
}
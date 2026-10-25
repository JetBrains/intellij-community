/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.console;


import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.common.EditorCaretTestUtil;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Yuli Fiterman
 */
@TestApplication
@RunInEdt(writeIntent = true)
public class ConsoleHistoryControllerTest {
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private LanguageConsoleImpl myConsole;
  private ConsoleHistoryController myHistoryController;
  private ConsoleExecuteAction myExecAction;

  @BeforeEach
  void setUp() {
    myConsole = new LanguageConsoleImpl(projectFixture.get(), "Test console", PlainTextLanguage.INSTANCE);
    myConsole.setConsoleEditorEnabled(true);
    myExecAction = new ConsoleExecuteAction(myConsole, new MockExecutionActionHandler());
    myExecAction.registerCustomShortcutSet(myExecAction.getShortcutSet(), myConsole.getConsoleEditor().getComponent());
    myHistoryController = new ConsoleHistoryController(new ConsoleRootType("test console", null) {
    }, null, myConsole);
    myHistoryController.setModel(PrefixHistoryModelKt.createModel("default", myConsole));
    myHistoryController.install();
    myConsole.setConsoleEditorEnabled(true);
  }

  @AfterEach
  void tearDown() {
    Disposer.dispose(myConsole);
  }

  private void setCaretWithText(String markedText) {
    myConsole.setInputText(markedText);
    EditorCaretTestUtil.CaretAndSelectionState state = EditorTestUtil.extractCaretAndSelectionMarkers(myConsole.getEditorDocument());
    EditorTestUtil.setCaretsAndSelection(myConsole.getConsoleEditor(), state);
    PsiDocumentManager.getInstance(projectFixture.get()).commitAllDocuments();
  }

  private void executeCommand() {
    PsiDocumentManager.getInstance(projectFixture.get()).commitAllDocuments();
    myExecAction.actionPerformed(TestActionEvent.createTestEvent());
  }

  private void execStatementList1() {
    myConsole.setInputText("Statement 1");
    executeCommand();
    myConsole.setInputText("Statement 2");
    executeCommand();

    myConsole.setInputText("Statement 3");
    executeCommand();

    myConsole.setInputText("Different Prefix");
    executeCommand();

    assertEquals("", myConsole.getEditorDocument().getText());
  }

  private void consoleNext() {
    myHistoryController.getHistoryNext().actionPerformed(AnActionEvent.createFromDataContext("test", null, DataContext.EMPTY_CONTEXT));
  }

  private void consolePrev() {
    myHistoryController.getHistoryPrev().actionPerformed(AnActionEvent.createFromDataContext("test", null, DataContext.EMPTY_CONTEXT));
  }

  private void checkResultByText(String expectedText) {
    Document expectedDocument = EditorFactory.getInstance().createDocument(expectedText);
    EditorCaretTestUtil.CaretAndSelectionState expectedState = EditorTestUtil.extractCaretAndSelectionMarkers(expectedDocument);
    assertEquals(expectedDocument.getText(), myConsole.getEditorDocument().getText());
    EditorTestUtil.verifyCaretAndSelectionState(myConsole.getConsoleEditor(), expectedState);
  }

  @Test
  public void testNavigateUp() {
    execStatementList1();
    setCaretWithText("Statement<caret> 4");
    consoleNext();
    checkResultByText("Statement 3<caret>");
  }

  @Test
  public void testNavigateDown() {
    execStatementList1();
    setCaretWithText("Statement<caret> 4");
    consoleNext();
    consolePrev();
    checkResultByText("Statement 4<caret>");
  }

  @Test
  public void testRepeatNavigateDown() {
    execStatementList1();
    setCaretWithText("Statement<caret> 4");
    consoleNext();
    consoleNext();
    consolePrev();
    consolePrev();
    checkResultByText("Statement 4<caret>");
  }

  //PY-26413
  @Test
  public void testNavigateDown2() {
    execStatementList1();
    setCaretWithText("<caret>Statement 4");
    consoleNext();
    consoleNext();
    setCaretWithText("Statement<caret> 3");
    consolePrev();
    checkResultByText("Different Prefix<caret>");
  }

  @Test
  public void testNavigateUpNoPrefix() {
    execStatementList1();
    setCaretWithText("<caret>Statement 4");
    consoleNext();
    checkResultByText("Different Prefix");
  }

  private static class MockExecutionActionHandler extends BaseConsoleExecuteActionHandler {

    MockExecutionActionHandler() {
      super(true);
    }
  }
}

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


import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestActionEvent;


/**
 * @author Yuli Fiterman
 */
public class ConsoleHistoryConstrollerTest extends LightPlatformCodeInsightTestCase {
  private LanguageConsoleImpl myConsole;
  private ConsoleHistoryController myHistoryController;
  private ConsoleExecuteAction myExecAction;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myConsole = new LanguageConsoleImpl(getProject(), "Test console", PlainTextLanguage.INSTANCE);
    myConsole.setConsoleEditorEnabled(true);
    myExecAction = new ConsoleExecuteAction(myConsole, new MockExecutionActionHandler());
    myExecAction.registerCustomShortcutSet(myExecAction.getShortcutSet(), myConsole.getConsoleEditor().getComponent());
    myHistoryController = new ConsoleHistoryController(new ConsoleRootType("test console", null) {
    }, null, myConsole);
    myHistoryController.setModel(PrefixHistoryModelKt.createModel("default", myConsole));
    myHistoryController.install();
    myConsole.setConsoleEditorEnabled(true);
    myEditor = myConsole.getConsoleEditor();
    myVFile = myConsole.getVirtualFile();
    myFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(myEditor.getDocument());
  }

  private void setCaretWithText(String markedText) {
    myConsole.setInputText(markedText);
    EditorTestUtil.CaretAndSelectionState state = EditorTestUtil.extractCaretAndSelectionMarkers(myConsole.getEditorDocument());
    EditorTestUtil.setCaretsAndSelection(myConsole.getConsoleEditor(), state);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  private void executeCommand() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    myExecAction.actionPerformed(new TestActionEvent());
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
    myHistoryController.getHistoryNext().actionPerformed(null);
  }

  private void consolePrev() {
    myHistoryController.getHistoryPrev().actionPerformed(null);
  }

  public void testNavigateUp() {
    execStatementList1();
    setCaretWithText("Statement<caret> 4");
    consoleNext();
    checkResultByText("Statement<caret> 3");
  }

  public void testNavigateDown() {
    execStatementList1();
    setCaretWithText("Statement<caret> 4");
    consoleNext();
    consolePrev();
    checkResultByText("Statement<caret> 4");
  }

  //PY-26413
  public void testNavigateDown2() {
    execStatementList1();
    setCaretWithText("<caret>Statement 4");
    consoleNext();
    consoleNext();
    setCaretWithText("Statement<caret> 3");
    consolePrev();
    checkResultByText("<caret>Different Prefix");
  }

  public void testNavigateUpNoPrefix() {
    execStatementList1();
    setCaretWithText("<caret>Statement 4");
    consoleNext();
    checkResultByText("Different Prefix");
  }

  @Override
  public void tearDown() throws Exception {
    try {

      Disposer.dispose(myConsole);
      myVFile = null;
    }
    finally {
      super.tearDown();
    }
  }

  private static class MockExecutionActionHandler extends BaseConsoleExecuteActionHandler {

    public MockExecutionActionHandler() {
      super(true);
    }
  }
}





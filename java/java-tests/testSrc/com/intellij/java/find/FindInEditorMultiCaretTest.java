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
package com.intellij.java.find;

import com.intellij.find.EditorSearchSession;
import com.intellij.find.editorHeaderActions.*;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import javax.swing.text.JTextComponent;

public class FindInEditorMultiCaretTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testBasic() {
    init("abc\n" +
         "abc\n" +
         "abc");
    initFind();
    setTextToFind("b");
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "abc\n" +
                      "abc");
    addOccurrence();
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "a<selection>b<caret></selection>c\n" +
                      "abc");
    nextOccurrence();
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "abc\n" +
                      "a<selection>b<caret></selection>c");
    prevOccurrence();
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "a<selection>b<caret></selection>c\n" +
                      "abc");
    removeOccurrence();
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "abc\n" +
                      "abc");
    allOccurrences();
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "a<selection>b<caret></selection>c\n" +
                      "a<selection>b<caret></selection>c");
    assertNull(getEditorSearchComponent());
  }

  public void testActionsInEditorWorkIndependently() {
    init("abc\n" +
         "abc\n" +
         "abc");
    initFind();
    setTextToFind("b");
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "abc\n" +
                      "abc");
    new EditorMouseFixture((EditorImpl)myFixture.getEditor()).clickAt(0, 1);
    addOccurrenceFromEditor();
    addOccurrenceFromEditor();
    checkResultByText("<selection>a<caret>bc</selection>\n" +
                      "<selection>a<caret>bc</selection>\n" +
                      "abc");
    nextOccurrenceFromEditor();
    checkResultByText("<selection>a<caret>bc</selection>\n" +
                      "abc\n" +
                      "<selection>a<caret>bc</selection>");
    prevOccurrenceFromEditor();
    checkResultByText("<selection>a<caret>bc</selection>\n" +
                      "<selection>a<caret>bc</selection>\n" +
                      "abc");
    removeOccurrenceFromEditor();
    checkResultByText("<selection>a<caret>bc</selection>\n" +
                      "abc\n" +
                      "abc");
    allOccurrencesFromEditor();
    checkResultByText("<selection>a<caret>bc</selection>\n" +
                      "<selection>a<caret>bc</selection>\n" +
                      "<selection>a<caret>bc</selection>");
    assertNotNull(getEditorSearchComponent());
  }

  public void testCloseRetainsMulticaretSelection() {
    init("abc\n" +
         "abc\n" +
         "abc");
    initFind();
    setTextToFind("b");
    addOccurrence();
    closeFind();
    checkResultByText("a<selection>b<caret></selection>c\n" +
                      "a<selection>b<caret></selection>c\n" +
                      "abc");
  }

  public void testTextModificationRemovesOldSelections() {
    init("abc\n" +
         "abc\n" +
         "abc");
    initFind();
    setTextToFind("b");
    addOccurrence();
    setTextToFind("bc");

    assertEquals(1, myFixture.getEditor().getCaretModel().getCaretCount());
    assertEquals("bc", myFixture.getEditor().getSelectionModel().getSelectedText());
  }

  public void testSecondFindNavigatesToTheSameOccurrence() {
    init("ab<caret>c\n" +
         "abc\n" +
         "abc");
    initFind();
    setTextToFind("abc");
    checkResultByText("abc\n" +
                      "<selection>abc<caret></selection>\n" +
                      "abc");
    closeFind();
    initFind();
    setTextToFind("abc");
    checkResultByText("abc\n" +
                      "<selection>abc<caret></selection>\n" +
                      "abc");
  }
  
  public void testFindNextRetainsOnlyOneCaretIfNotUsedAsMoveToNextOccurrence() {
    init("<caret>To be or not to be?");
    initFind();
    setTextToFind("be");
    checkResultByText("To <selection>be<caret></selection> or not to be?");
    closeFind();
    new EditorMouseFixture((EditorImpl)myFixture.getEditor()).alt().shift().clickAt(0, 8); // adding second caret
    checkResultByText("To <selection>be<caret></selection> or<caret> not to be?");
    nextOccurrenceFromEditor();
    checkResultByText("To be or not to <selection>be<caret></selection>?");
  }

  public void testSelectAllDuringReplace() {
    init("some text");
    initReplace();
    setTextToFind("e");
    allOccurrences();
    checkResultByText("som<selection>e<caret></selection> t<selection>e<caret></selection>xt");
  }

  private void setTextToFind(String text) {
    EditorSearchSession editorSearchSession = getEditorSearchComponent();
    assertNotNull(editorSearchSession);
    JTextComponent searchField = editorSearchSession.getComponent().getSearchTextComponent();
    assertNotNull(searchField);
    for (int i = 0; i <= text.length(); i++) {
      searchField.setText(text.substring(0, i)); // emulate typing chars one by one
      IdeEventQueue.getInstance().flushQueue();
    }
  }

  private void nextOccurrence() {
    executeHeaderAction(new NextOccurrenceAction());
  }

  private void prevOccurrence() {
    executeHeaderAction(new PrevOccurrenceAction());
  }

  private void addOccurrence() {
    executeHeaderAction(new AddOccurrenceAction());
  }

  private void removeOccurrence() {
    executeHeaderAction(new RemoveOccurrenceAction());
  }

  private void allOccurrences() {
    executeHeaderAction(new SelectAllAction());
  }

  private void nextOccurrenceFromEditor() {
    myFixture.performEditorAction(IdeActions.ACTION_FIND_NEXT);
  }

  private void prevOccurrenceFromEditor() {
    myFixture.performEditorAction(IdeActions.ACTION_FIND_PREVIOUS);
  }

  private void addOccurrenceFromEditor() {
    myFixture.performEditorAction(IdeActions.ACTION_SELECT_NEXT_OCCURENCE);
  }

  private void removeOccurrenceFromEditor() {
    myFixture.performEditorAction(IdeActions.ACTION_UNSELECT_PREVIOUS_OCCURENCE);
  }

  private void allOccurrencesFromEditor() {
    myFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL_OCCURRENCES);
  }

  private void closeFind() {
    EditorSearchSession editorSearchSession = getEditorSearchComponent();
    editorSearchSession.close();
  }

  private void executeHeaderAction(AnAction action) {
    DataContext context = new DataManagerImpl.MyDataContext(getEditorSearchComponent().getComponent());
    AnActionEvent e = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_TOOLBAR, null, context);
    action.beforeActionPerformedUpdate(e);
    if (e.getPresentation().isEnabled() && e.getPresentation().isVisible()) {
      action.actionPerformed(e);
    }
  }

  private void initFind() {
    myFixture.performEditorAction(IdeActions.ACTION_FIND);
  }

  private void initReplace() {
    myFixture.performEditorAction(IdeActions.ACTION_REPLACE);
  }

  private EditorSearchSession getEditorSearchComponent() {
    return EditorSearchSession.get(myFixture.getEditor());
  }
  
  private void init(String text) {
    myFixture.configureByText(getTestName(false) + ".txt", text);
  }

  private void checkResultByText(String text) {
    myFixture.checkResult(text);
  }
}

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find;

import com.intellij.find.editorHeaderActions.*;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import javax.swing.text.JTextComponent;
import java.io.IOException;

public class FindInEditorMultiCaretTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testBasic() throws IOException {
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

  public void testActionsInEditorWorkIndependently() throws IOException {
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

  public void testCloseRetainsMulticaretSelection() throws IOException {
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

  public void testTextModificationRemovesOldSelections() throws IOException {
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

  public void testSecondFindNavigatesToTheSameOccurrence() throws IOException {
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

  private void setTextToFind(String text) {
    EditorSearchComponent editorSearchComponent = getEditorSearchComponent();
    assertNotNull(editorSearchComponent);
    JTextComponent searchField = editorSearchComponent.getSearchTextComponent();
    assertNotNull(searchField);
    for (int i = 0; i <= text.length(); i++) {
      searchField.setText(text.substring(0, i)); // emulate typing chars one by one
      IdeEventQueue.getInstance().flushQueue();
    }
  }

  private void nextOccurrence() {
    final EditorSearchComponent editorSearchComponent = getEditorSearchComponent();
    executeAction(new NextOccurrenceAction(editorSearchComponent, editorSearchComponent.getSearchTextComponent(), true));
  }

  private void prevOccurrence() {
    final EditorSearchComponent editorSearchComponent = getEditorSearchComponent();
    executeAction(new PrevOccurrenceAction(editorSearchComponent, editorSearchComponent.getSearchTextComponent()));
  }

  private void addOccurrence() {
    executeAction(new AddOccurrenceAction(getEditorSearchComponent()));
  }

  private void removeOccurrence() {
    executeAction(new RemoveOccurrenceAction(getEditorSearchComponent()));
  }

  private void allOccurrences() {
    executeAction(new SelectAllAction(getEditorSearchComponent()));
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
    EditorSearchComponent editorSearchComponent = getEditorSearchComponent();
    executeAction(new CloseOnESCAction(editorSearchComponent, editorSearchComponent.getSearchTextComponent()));
  }

  private static void executeAction(EditorHeaderAction action) {
    action.actionPerformed(AnActionEvent.createFromInputEvent(action, null, ActionPlaces.EDITOR_TOOLBAR));
  }

  private void initFind() {
    myFixture.performEditorAction("Find");
  }

  private EditorSearchComponent getEditorSearchComponent() {
    return (EditorSearchComponent)myFixture.getEditor().getHeaderComponent();
  }
  
  private void init(String text) {
    myFixture.configureByText(getTestName(false) + ".txt", text);
  }

  private void checkResultByText(String text) {
    myFixture.checkResult(text);
  }
}

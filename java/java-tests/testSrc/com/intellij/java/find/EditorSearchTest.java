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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorSearchTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  private static final String THE_CODE = """
    public class A {
      //First comment ABC, ABCD, abc
      int ABC = 3;
      int abcde = 4;
      String literalString = "ABC, ABCD, abc";
    }""";

  @Test
  public void testSearchFieldSelection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", THE_CODE);
    EdtTestUtil.runInEdtAndWait(() -> {
      String key = "ABC";
      int i = THE_CODE.indexOf(key);
      myFixture.getEditor().getSelectionModel().setSelection(i, i+key.length());
      myFixture.performEditorAction("Find");
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      assertEquals(key, getSearchTextComponent().getText());
      assertEquals(key, getSearchTextComponent().getSelectedText());
      assertEquals(3, getSearchTextComponent().getCaretPosition());
      assertTrue(getEditorSearchComponent().hasMatches());
      IdeFocusManager.findInstance().requestFocus(myFixture.getEditor().getContentComponent(), false);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      myFixture.performEditorAction("Find");
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      assertEquals(key, getSearchTextComponent().getText());
      assertEquals(key, getSearchTextComponent().getSelectedText());
      assertEquals(key.length(), getSearchTextComponent().getCaretPosition());

      getEditorSearchComponent().close();

      key = "abcde";
      i = THE_CODE.indexOf(key);
    
      myFixture.getEditor().getSelectionModel().setSelection(i, i+key.length());
      int selectionStart = myFixture.getEditor().getSelectionModel().getSelectionStart();
      int selectionEnd = myFixture.getEditor().getSelectionModel().getSelectionEnd();
      int caret = myFixture.getEditor().getCaretModel().getOffset();

      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      myFixture.performEditorAction("Find");
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      assertEquals(key, getSearchTextComponent().getText());
      assertEquals(key, getSearchTextComponent().getSelectedText());
      assertEquals(key.length(), getSearchTextComponent().getCaretPosition());

      try {
        getSearchTextComponent().getDocument().remove(0, getSearchTextComponent().getDocument().getLength());
      } catch (BadLocationException ignore) {}
    
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      assertEquals(selectionStart, myFixture.getEditor().getSelectionModel().getSelectionStart());
      assertEquals(selectionEnd, myFixture.getEditor().getSelectionModel().getSelectionEnd());
      assertEquals(caret, myFixture.getEditor().getCaretModel().getOffset());
    });
  }

  private EditorSearchSession getEditorSearchComponent() {
    return EditorSearchSession.get(codeInsightFixture.get().getEditor());
  }

  private JTextComponent getSearchTextComponent() {
    return getEditorSearchComponent().getComponent().getSearchTextComponent();
  }
}

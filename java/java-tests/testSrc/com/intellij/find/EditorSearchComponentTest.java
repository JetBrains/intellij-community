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
package com.intellij.find;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class EditorSearchComponentTest extends LightPlatformCodeInsightFixtureTestCase {
  private static final String THE_CODE = "public class A {\n" +
                                   "  //First comment ABC, ABCD, abc\n" +
                                   "  int ABC = 3;\n" +
                                   "  int abcde = 4;\n" +
                                   "  String literalString = \"ABC, ABCD, abc\";\n" +
                                   "}";

  public void testSearchFieldSelection() {
    myFixture.configureByText("a.java", THE_CODE);
    String key = "ABC";
    int i = THE_CODE.indexOf(key);
    myFixture.getEditor().getSelectionModel().setSelection(i, i+key.length());
    myFixture.performEditorAction("Find");
    IdeEventQueue.getInstance().flushQueue();
    assertEquals(key, getEditorSearchComponent().getSearchTextComponent().getText());
    assertEquals(key, getEditorSearchComponent().getSearchTextComponent().getSelectedText());
    assertEquals(3, getEditorSearchComponent().getSearchTextComponent().getCaretPosition());
    assertTrue(getEditorSearchComponent().hasMatches());
    IdeFocusManager.findInstance().requestFocus(myFixture.getEditor().getContentComponent(), false);
    IdeEventQueue.getInstance().flushQueue();

    myFixture.performEditorAction("Find");
    IdeEventQueue.getInstance().flushQueue();
    assertEquals(key, getEditorSearchComponent().getSearchTextComponent().getText());
    assertEquals(key, getEditorSearchComponent().getSearchTextComponent().getSelectedText());
    assertEquals(key.length(), getEditorSearchComponent().getSearchTextComponent().getCaretPosition());

    getEditorSearchComponent().close();

    key = "abcde";
    i = THE_CODE.indexOf(key);
    myFixture.getEditor().getSelectionModel().setSelection(i, i+key.length());
    IdeEventQueue.getInstance().flushQueue();
    myFixture.performEditorAction("Find");
    IdeEventQueue.getInstance().flushQueue();
    assertEquals(key, getEditorSearchComponent().getSearchTextComponent().getText());
    assertEquals(key, getEditorSearchComponent().getSearchTextComponent().getSelectedText());
    assertEquals(key.length(), getEditorSearchComponent().getSearchTextComponent().getCaretPosition());

  }

  private EditorSearchComponent getEditorSearchComponent() {
    return (EditorSearchComponent)myFixture.getEditor().getHeaderComponent();
  }

}

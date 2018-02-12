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
package org.intellij.lang.regexp;

import com.intellij.codeInsight.editorActions.SelectWordHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RegExpWordSelectionFilterTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testSelectWordSkipChar() {
    doTest("graafm<caret>achine",
           "<selection>graafmachine</selection>");
  }

  public void testSelectHexChar() {
    doTest("graafm\\x<caret>61chine",
           "graafm\\<selection>x61</selection>chine",
           "graafm<selection>\\x61</selection>chine",
           "<selection>graafm\\x61chine</selection>");
  }

  public void testSelectOctalChar() {
    doTest("graafm\\01<caret>4chine",
           "graafm\\<selection>014</selection>chine",
           "graafm<selection>\\014</selection>chine",
           "<selection>graafm\\014chine</selection>");
  }

  public void testSelectUnicodeEscape() {
    doTest("graafm\\u0<caret>061chine",
           "graafm\\<selection>u0061</selection>chine",
           "graafm<selection>\\u0061</selection>chine",
           "<selection>graafm\\u0061chine</selection>");
  }

  public void testSelectNamedCharacter() {
    doTest("graafm\\N{LATIN SMALL<caret> LETTER A}chine",
           "graafm\\N{LATIN <selection>SMALL</selection> LETTER A}chine",
           "graafm\\N{<selection>LATIN SMALL LETTER A</selection>}chine",
           "graafm<selection>\\N{LATIN SMALL LETTER A}</selection>chine",
           "<selection>graafm\\N{LATIN SMALL LETTER A}chine</selection>");
  }

  public void doTest(@NotNull final String before, final String... afters) {
    assert afters != null && afters.length > 0;
    myFixture.configureByText("test.regexp", before);

    final SelectWordHandler action = new SelectWordHandler(null);
    final DataContext dataContext = DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());
    final Editor editor = myFixture.getEditor();
    for (String after : afters) {
      action.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
      myFixture.checkResult(after);
    }
  }
}
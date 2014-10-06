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
package com.intellij.codeInsight.highlighting;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JavaBraceMatcherTest extends LightCodeInsightFixtureTestCase {
  public void testGenerics() {
    myFixture.configureByText("a.java", "import java.util.ArrayList;" +
                                        "class A {" +
                                        "  ArrayList<caret><? extends @Anno String[]> f;" +
                                        "}");
    final int offset = BraceMatchingUtil.getMatchedBraceOffset(myFixture.getEditor(), true, myFixture.getFile());
    assertEquals(72, offset);
  }

  public void testBrokenText() {
    myFixture.configureByText("a.java", "import java.util.ArrayList;" +
                                        "class A {" +
                                        "  ArrayList<caret><String");
    final Editor editor = myFixture.getEditor();
    final EditorHighlighter editorHighlighter = ((EditorEx)editor).getHighlighter();
    final HighlighterIterator iterator = editorHighlighter.createIterator(editor.getCaretModel().getOffset());
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), myFixture.getFile().getFileType(), iterator,
                                                   true);
    assertFalse(matched);
  }

  public void testBinaryStatement() {
    myFixture.configureByText("a.java", "import java.util.ArrayList;" +
                                        "class A {" +
                                        "  int i = 3 <caret>< 4 ? 5 > 6 : 1 : 1 : 1;" +
                                        "}");
    final Editor editor = myFixture.getEditor();
    final EditorHighlighter editorHighlighter = ((EditorEx)editor).getHighlighter();
    final HighlighterIterator iterator = editorHighlighter.createIterator(editor.getCaretModel().getOffset());
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), myFixture.getFile().getFileType(), iterator, true);
    assertFalse(matched);
  }



}

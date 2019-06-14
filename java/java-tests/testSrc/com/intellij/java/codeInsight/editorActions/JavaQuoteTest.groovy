// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.editorActions

import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

@CompileStatic
class JavaQuoteTest extends LightCodeInsightFixtureTestCase {
  void testDouble1() { doTest '<caret>', '"<caret>"' }
  void testDouble2() { doTest '<caret>""', '"<caret>"""' }
  void testDoubleClosing() { doTest '"<caret>"', '""<caret>' }
  void testDoubleClosingWithText() { doTest '"text<caret>"', '"text"<caret>' }
  void testDoubleEscape() { doTest ' "\\<caret>" ', ' "\\"<caret>" ' }
  void testDoubleEscapeClosing() { doTest ' "\\\\<caret>" ', ' "\\\\"<caret> ' }
  void testBeforeIdentifier() { doTest 'foo(<caret>a);', 'foo("<caret>a);' }
  void testDoubleInString() { doTest '"Hello"<caret> world";', '"Hello""<caret> world";' }
  void testBeforeStringWithEscape() { doTest 'foo(P + <caret>"\\n" + "xxx" + E)', 'foo(P + "<caret>""\\n" + "xxx" + E)' }

  void testSingleInString() { doTest ' "<caret>" ', ' "\'<caret>" ', "'" as char }
  void testSingleInComment() { doTest '/* <caret> */', '/* \'<caret> */', "'" as char }
  void testSingleInStringAfterEscape() { doTest ''' split(text, '\\<caret>); ''', ''' split(text, '\\'<caret>); ''', "'" as char }

  private void doTest(String before, String after, char c = '"') {
    myFixture.configureByText("a.java", "class C {{\n  ${before}\n}}")
    EditorActionManager.instance.typedAction.actionPerformed(editor, c, (editor as EditorEx).dataContext)
    myFixture.checkResult("class C {{\n  ${after}\n}}")
  }
}
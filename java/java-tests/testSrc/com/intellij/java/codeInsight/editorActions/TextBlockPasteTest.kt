// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions

import com.intellij.JavaTestUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.awt.datatransfer.StringSelection

class TextBlockPasteTest: LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/editorActions/textBlockPaste"

  fun testPasteSimple() {
    doTest("""
      first line
      second line
    """.trimIndent())
  }

  fun testPasteWhitespaceLineInTheMiddle() {
    doTest("""
      first line
          
      second line
         
      third line
    """.trimIndent())
  }

  fun testPasteWhitespaceLineInTheEnd() {
    doTest("""
      first line
      second line
            
          
    """.trimIndent())
  }

  fun testPasteWhitespaceLineInTheBeginning() {
    doTest("""
          
           
      first line
      second line
    """.trimIndent())
  }

  fun testPasteLineWithWhitespacesInTheEnd() {
    doTest("""
      first line with whitespaces in the end            
      second line with whitespaces in the end     
    """.trimIndent())
  }

  private fun doTest(text: String) {
    CopyPasteManager.getInstance().setContents(StringSelection(text))
    myFixture.configureByFile("before${getTestName(false)}.java")
    WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformatText(file, 0, editor.document.textLength) }
    myFixture.checkResultByFile("before${getTestName(false)}.java")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
    myFixture.checkResultByFile("after${getTestName(false)}.java")
  }
}
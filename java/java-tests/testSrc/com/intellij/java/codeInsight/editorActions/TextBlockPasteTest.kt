// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions

import com.intellij.JavaTestUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.datatransfer.StringSelection

@TestApplication
class TextBlockPasteTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/editorActions/textBlockPaste"
  }

  @Test
  fun testPasteSimple() {
    doTest("""
      first line
      second line
    """.trimIndent())
  }

  @Test
  fun testPasteWhitespaceLineInTheMiddle() {
    doTest("""
      first line
          
      second line
         
      third line
    """.trimIndent())
  }

  @Test
  fun testPasteWhitespaceLineInTheEnd() {
    doTest("""
      first line
      second line
            
          
    """.trimIndent())
  }

  @Test
  fun testPasteWhitespaceLineInTheBeginning() {
    doTest("""
          
           
      first line
      second line
    """.trimIndent())
  }

  @Test
  fun testPasteLineWithWhitespacesInTheEnd() {
    doTest("""
      first line with whitespaces in the end            
      second line with whitespaces in the end     
    """.trimIndent())
  }

  private fun doTest(text: String) {
    CopyPasteManager.getInstance().setContents(StringSelection(text))
    myFixture.configureByFile("before${myFixture.getTestName(false)}.java")
    WriteCommandAction.runWriteCommandAction(myFixture.project) { CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, 0, myFixture.editor.document.textLength) }
    myFixture.checkResultByFile("before${myFixture.getTestName(false)}.java")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
    myFixture.checkResultByFile("after${myFixture.getTestName(false)}.java")
  }
}
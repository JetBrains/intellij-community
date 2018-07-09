// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.actions

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaTokenType
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

private typealias BraceMatcher = (HighlighterIterator, CharSequence, FileType) -> Int
private val TARGET_MARKER = "<target>"

class BraceMatcherUtilTest : LightPlatformCodeInsightTestCase() {
  fun testRightmostRParen0() = assertRightmostRParenth("(<caret>()([{()}])<target>)[]()")
  fun testRightmostRParen1() = assertRightmostRParenth("(<caret>(})([{()}]))[]()")
  fun testRightmostRParen2() = assertRightmostRParenth("(<caret>({})([{()}](){})<target>)[]()")
  fun testRightmostRParen3() = assertRightmostRParenth("(<caret>")
  fun testRightmostRParen4() = assertRightmostRParenth("(<caret><target>)")
  fun testRightmostRParen5() = assertRightmostRParenth("((<caret><target>)")
  fun testRightmostRParen6() = assertRightmostRParenth("(<caret>)<target>)")
  fun testRightmostRParen7() = assertRightmostRParenth("((<caret>)<target>)")
  fun testRightmostRParen8() = assertRightmostRParenth("<caret>")
  fun testRightmostRParen9() = assertRightmostRParenth("<caret><target>)")

  fun testLeftmostLParen0() = assertLeftmostLParenth("(()([{()}]))<caret>")
  fun testLeftmostLParen1() = assertLeftmostLParenth("(()([{()}])<caret>)")
  fun testLeftmostLParen2() = assertLeftmostLParenth("<target>((()([{()}])<caret>)")
  fun testLeftmostLParen3() = assertLeftmostLParenth("<target>(()(()([{()}])<caret>)")

  fun testLeftLParen0() = assertLeftLParenth("(<caret>)")
  fun testLeftLParen1() = assertLeftLParenth("<target>((<caret>)")
  fun testLeftLParen2() = assertLeftLParenth("()<target>((<caret>)")
  fun testLeftLParen3() = assertLeftLParenth("<target>(<caret>")


  private fun assertRightmostRParenth(text: String) {
    assertParenthScanning(text) { iterator, charSequence, fileType ->
      BraceMatchingUtil.findRightmostRParen(iterator, JavaTokenType.RPARENTH, charSequence, fileType)
    }
  }

  private fun assertLeftmostLParenth(text: String) {
    assertParenthScanning(text) { iterator, charSequence, fileType ->
      BraceMatchingUtil.findLeftmostLParen(iterator, JavaTokenType.LPARENTH, charSequence, fileType)
    }
  }

  private fun assertLeftLParenth(text: String) {
    assertParenthScanning(text) { iterator, charSequence, fileType ->
      BraceMatchingUtil.findLeftLParen(iterator, JavaTokenType.LPARENTH, charSequence, fileType)
    }
  }

  private fun assertParenthScanning(text: String, f: BraceMatcher) {
    val document: Document = configureFromFileText("foo.java", text)

    val expectedIndex: Int = StringUtil.indexOf(document.charsSequence, TARGET_MARKER)
    if (expectedIndex >= 0) {
      WriteAction.run<Throwable> {
        document.deleteString(expectedIndex, expectedIndex + TARGET_MARKER.length)
      }
    }

    val offset: Int = getEditor().caretModel.offset
    val iterator: HighlighterIterator = (getEditor() as EditorEx).highlighter.createIterator(offset)
    val fileType: FileType = getFile().virtualFile.fileType
    val actualIndex: Int = f(iterator, document.charsSequence, fileType)

    assertEquals(expectedIndex, actualIndex)
  }
}

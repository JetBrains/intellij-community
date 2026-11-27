// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.actions

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.Companion.lightDaemonAnalyzerFixture
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaTokenType
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private typealias BraceMatcher = (HighlighterIterator, CharSequence, FileType) -> Int
private const val TARGET_MARKER = "<target>"

@TestApplication
class BraceMatcherUtilTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val daemonFixture = lightDaemonAnalyzerFixture(projectFixture)

  @Test fun testRightmostRParen0(): Unit = assertRightmostRParenth("(<caret>()([{()}])<target>)[]()")
  @Test fun testRightmostRParen1(): Unit = assertRightmostRParenth("(<caret>(})([{()}]))[]()")
  @Test fun testRightmostRParen2(): Unit = assertRightmostRParenth("(<caret>({})([{()}](){})<target>)[]()")
  @Test fun testRightmostRParen3(): Unit = assertRightmostRParenth("(<caret>")
  @Test fun testRightmostRParen4(): Unit = assertRightmostRParenth("(<caret><target>)")
  @Test fun testRightmostRParen5(): Unit = assertRightmostRParenth("((<caret><target>)")
  @Test fun testRightmostRParen6(): Unit = assertRightmostRParenth("(<caret>)<target>)")
  @Test fun testRightmostRParen7(): Unit = assertRightmostRParenth("((<caret>)<target>)")
  @Test fun testRightmostRParen8(): Unit = assertRightmostRParenth("<caret>")
  @Test fun testRightmostRParen9(): Unit = assertRightmostRParenth("<caret><target>)")

  @Test fun testLeftmostLParen0(): Unit = assertLeftmostLParenth("(()([{()}]))<caret>")
  @Test fun testLeftmostLParen1(): Unit = assertLeftmostLParenth("(()([{()}])<caret>)")
  @Test fun testLeftmostLParen2(): Unit = assertLeftmostLParenth("<target>((()([{()}])<caret>)")
  @Test fun testLeftmostLParen3(): Unit = assertLeftmostLParenth("<target>(()(()([{()}])<caret>)")

  @Test fun testLeftLParen0(): Unit = assertLeftLParenth("(<caret>)")
  @Test fun testLeftLParen1(): Unit = assertLeftLParenth("<target>((<caret>)")
  @Test fun testLeftLParen2(): Unit = assertLeftLParenth("()<target>((<caret>)")
  @Test fun testLeftLParen3(): Unit = assertLeftLParenth("<target>(<caret>")


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

  private fun assertParenthScanning(text: String, f: BraceMatcher): Unit = timeoutRunBlocking {
    val daemon = daemonFixture.get()
    daemon.configureFromFileText("foo.java", text)
    val editor = daemon.fixture.editor
    val document: Document = editor.document

    val expectedIndex: Int = StringUtil.indexOf(document.charsSequence, TARGET_MARKER)
    if (expectedIndex >= 0) {
      WriteCommandAction.runWriteCommandAction(daemon.fixture.project) {
        document.deleteString(expectedIndex, expectedIndex + TARGET_MARKER.length)
      }
    }

    val actualIndex: Int = readAction {
      val offset: Int = editor.caretModel.offset
      val iterator: HighlighterIterator = editor.highlighter.createIterator(offset)
      val fileType: FileType = daemon.fixture.file.virtualFile.fileType
      f(iterator, document.charsSequence, fileType)
    }

    assertEquals(expectedIndex, actualIndex)
  }
}

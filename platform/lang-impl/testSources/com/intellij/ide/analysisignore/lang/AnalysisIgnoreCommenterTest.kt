// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.ide.analysisignore.ANALYSIS_IGNORE_FILE_NAME
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.EditorTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * The comment action puts `#` in column 0, because the reader takes only such a `#` as a comment.
 */
@TestApplication
class AnalysisIgnoreCommenterTest {
  private companion object {
    // The comment action commits the document, and a synchronous commit needs an open project.
    val projectFixture = projectFixture(openAfterCreation = true)
    val sourceRootFixture = projectFixture.moduleFixture().sourceRootFixture()

    @Suppress("unused")
    private val testDataContextInstaller = testFixture {
      val project = projectFixture.init()
      val application = TestApplicationManager.getInstance()
      application.setDataProvider(TestDataProvider(project))

      initialized(Unit) {
        application.setDataProvider(null)
      }
    }
  }

  private val psiFileFixture = sourceRootFixture.psiFileFixture(ANALYSIS_IGNORE_FILE_NAME, "build/\n")
  private val editorFixture = psiFileFixture.editorFixture()

  private val project by projectFixture
  private val psiFile by psiFileFixture
  private val editor by editorFixture

  @Test
  @Timeout(30)
  fun `the file has the language and the file type`(): Unit = timeoutRunBlocking {
    readAction {
      assertSame(AnalysisIgnoreLanguage, psiFile.language)
      assertSame(AnalysisIgnoreFileType, psiFile.fileType)
    }
  }

  @Test
  @Timeout(30)
  fun `comment puts the hash in column 0 without a space`() {
    doTest(before = "<caret>  build/\n", after = "#  build/\n")
  }

  @Test
  @Timeout(30)
  fun `uncomment removes the hash`() {
    doTest(before = "<caret>#build/\n", after = "build/\n")
  }

  @Test
  @Timeout(30)
  fun `comment applies to each line of the selection`() {
    doTest(before = "<selection>build/\nout/</selection>\n", after = "#build/\n#out/\n")
  }

  private fun doTest(before: String, after: String): Unit = timeoutRunBlocking {
    val actual = withContext(Dispatchers.EDT) {
      val document = editor.document
      WriteCommandAction.runWriteCommandAction(project) {
        document.setText(before)
        EditorTestUtil.setCaretsAndSelection(editor, EditorTestUtil.extractCaretAndSelectionMarkers(document))
      }
      PsiDocumentManager.getInstance(project).commitDocument(document)

      val performed = EditorTestFixture(project, editor, psiFile.virtualFile).performEditorAction(IdeActions.ACTION_COMMENT_LINE)
      assertTrue(performed, "The comment action is disabled")
      document.text
    }
    assertEquals(after, actual)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
internal class IdentifierHighlightingCommitTest {
  companion object {
    private val projectFixture: TestFixture<Project> = projectFixture(openAfterCreation = true)
    private val sourceRoot: TestFixture<PsiDirectory> = projectFixture.moduleFixture().sourceRootFixture()
  }

  private val fileFixture: TestFixture<PsiFile> =
    sourceRoot.psiFileFixture("Test.java", "class Test { int value; void test() { val<caret>ue++; } }")
  private val editorFixture: TestFixture<Editor> = fileFixture.editorFixture()

  @Test
  @TestFor(issues = ["IJPL-252880"])
  fun `typing does not cause identifier highlights to blink when trying to highlight uncommitted document`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking(context = Dispatchers.Default) {
      val project = projectFixture.get()
      suspendUntilIndexesAreReady(project)
      val editor = editorFixture.get()
      val document = editor.document
      val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerEx
      val file = readAction { requireNotNull(documentManager.getPsiFile(document)) }
      val initialResult = collect(file, editor)
      assertEquals(2, initialResult.occurrences.size)
      apply(file, editor, initialResult)

      var removals = 0
      withContext(Dispatchers.EDT) {
        documentManager.disableBackgroundCommit(disposable)
        (editor.markupModel as MarkupModelEx).addMarkupModelListener(disposable, object : MarkupModelListener {
          override fun beforeRemoved(highlighter: RangeHighlighterEx) {
            if (HighlightInfo.fromRangeHighlighter(highlighter)?.severity == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY) {
              removals++
            }
          }
        })
        WriteCommandAction.runWriteCommandAction(project) {
          document.insertString(editor.caretModel.offset - 3, " ".repeat(100))
        }
        assertFalse(documentManager.isCommitted(document))
      }

      val pendingResult = async(start = CoroutineStart.UNDISPATCHED) { collect(file, editor) }
      try {
        if (pendingResult.isCompleted) {
          apply(file, editor, pendingResult.await())
        }
        assertEquals(0, removals, "The identifier highlights must stay while the document is uncommitted")
        assertFalse(pendingResult.isCompleted, "The computation must wait for the PSI commit")

        withContext(Dispatchers.EDT) {
          documentManager.commitDocument(document)
        }
        val result = pendingResult.await()
        assertEquals(2, result.occurrences.size)
        readAction {
          for (occurrence in result.occurrences) {
            assertEquals("value", document.charsSequence.subSequence(occurrence.range.startOffset, occurrence.range.endOffset).toString())
          }
        }
        apply(file, editor, result)
        assertEquals(0, removals, "The committed result must reuse the identifier highlighters")
      }
      finally {
        withContext(NonCancellable) {
          pendingResult.cancelAndJoin()
          withContext(Dispatchers.EDT) {
            documentManager.commitDocument(document)
          }
        }
      }
    }

  private suspend fun collect(file: PsiFile, editor: Editor): IdentifierHighlightingResult {
    val (visibleRange, offset) = readAction {
      ProperTextRange(0, editor.document.textLength) to editor.caretModel.offset
    }
    return IdentifierHighlightingAccessorImpl.getMarkupData(file, editor, visibleRange, offset)
  }

  @Suppress("DEPRECATION")
  private suspend fun apply(file: PsiFile, editor: Editor, result: IdentifierHighlightingResult) {
    val infos = readAction {
      IdentifierHighlighterUpdater(file, editor, EditorContextManager.getEditorContext(editor, file.project), file)
        .createHighlightInfos(result)
    }
    withContext(Dispatchers.EDT) {
      val group = (IdentifierHighlightingManager.getInstance(file.project) as IdentifierHighlightingManagerImpl).getPassId()
      UpdateHighlightersUtil.setHighlightersToSingleEditor(file.project, editor, 0, editor.document.textLength, infos, editor.colorsScheme, group)
    }
  }
}

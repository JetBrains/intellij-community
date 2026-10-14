// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.LineStatusMarkerRenderer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.DocumentUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class ReviewGutterRangeCompositionTest {
  private val headContent: String = (0..39).joinToString("\n") { "line %02d".format(it) }
  private val reviewRanges: List<Range> = listOf(Range(28, 30, 30, 34), Range(35, 36, 37, 38))

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `local edits keep review ranges inside the document`(): Unit = timeoutRunBlocking {
    withProbe { probe ->
      probe.edit { it.deleteString(0, it.getLineStartOffset(12)) }
      probe.assertRanges(listOf(Range(28, 30, 18, 22), Range(35, 36, 25, 26)))
      probe.edit { it.insertString(0, "added\n") }
      probe.assertRanges(listOf(Range(28, 30, 19, 23), Range(35, 36, 26, 27)))
      probe.edit { it.setText("") }
      probe.assertRanges(emptyList())
      probe.awaitHighlighters(emptyList())
    }
  }

  @Test
  fun `bulk updates hide ranges outside the document`(): Unit = timeoutRunBlocking {
    withProbe { probe ->
      probe.inBulk {
        probe.edit { it.deleteString(0, it.getLineStartOffset(20)) }
        assertTrue(probe.model.getRanges()!!.last().line2 > probe.document.lineCount)
        assertNull(probe.renderer.visibleRanges())
      }
      val expected = listOf(Range(28, 30, 10, 14), Range(35, 36, 17, 18))
      probe.assertRanges(expected)
      probe.awaitHighlighters(expected)
    }
  }

  @Test
  fun `bulk updates hide stale ranges that still fit`(): Unit = timeoutRunBlocking {
    withProbe { probe ->
      probe.inBulk {
        probe.edit { it.insertString(0, "added\n") }
        assertNull(probe.renderer.visibleRanges())
      }
      val expected = listOf(Range(28, 30, 31, 35), Range(35, 36, 38, 39))
      probe.assertRanges(expected)
      probe.awaitHighlighters(expected)
    }
  }

  @Test
  fun `ranges return after a bulk update with no net change`(): Unit = timeoutRunBlocking {
    withProbe { probe ->
      probe.inBulk {
        probe.edit { it.setText("") }
        assertNull(probe.renderer.visibleRanges())
        probe.edit { it.setText(headContent) }
      }
      probe.assertRanges(reviewRanges)
      probe.awaitHighlighters(reviewRanges)
    }
  }

  @Test
  fun `ranges recover after bulk edits without a model emission`(): Unit = timeoutRunBlocking {
    withProbe(trackDocument = false, mockEditor = true) { probe ->
      probe.inBulk {
        probe.awaitHighlighters(emptyList())
        probe.edit { it.setText("") }
        probe.edit { it.setText(headContent) }
      }
      probe.awaitHighlighters(reviewRanges)
    }
  }

  @Test
  fun `stale ranges stay hidden until the model catches up`(): Unit = timeoutRunBlocking {
    withProbe(trackDocument = false) { probe ->
      probe.inBulk {
        probe.edit { it.deleteString(0, it.getLineStartOffset(20)) }
        assertNull(probe.renderer.visibleRanges())
      }
      assertTrue(probe.model.isValid())
      assertNull(probe.renderer.visibleRanges())
      probe.awaitHighlighters(emptyList())
      val expected = listOf(Range(28, 30, 10, 14), Range(35, 36, 17, 18))
      probe.setRanges(expected)
      probe.assertRanges(expected)
      probe.awaitHighlighters(expected)
    }
  }

  @Test
  fun `the renderer accepts a deletion at the end of the document`(): Unit = timeoutRunBlocking {
    val deletion = listOf(Range(40, 42, 40, 40))
    withProbe(patchHunks = deletion) { probe ->
      probe.assertRanges(deletion)
      probe.awaitHighlighters(deletion)
    }
  }

  private suspend fun withProbe(
    patchHunks: List<Range> = reviewRanges,
    trackDocument: Boolean = true,
    mockEditor: Boolean = false,
    body: suspend (Probe) -> Unit,
  ) {
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance()
      val document = factory.createDocument(headContent)
      val editor = if (mockEditor) mockk<Editor> {
        every { getDocument() } returns document
        every { getProject() } returns null
      } else factory.createEditor(document)
      val disposable = Disposer.newDisposable()
      try {
        val probe = Probe(editor, patchHunks, disposable)
        val rendererJob = launch(start = CoroutineStart.UNDISPATCHED) {
          probe.model.reviewRanges.collect { probe.renderer.scheduleUpdate() }
        }
        val trackerJob = if (trackDocument) launch(start = CoroutineStart.UNDISPATCHED) {
          ReviewInEditorUtil.trackDocumentDiffSync(headContent, editor.document, probe::publish)
        } else null
        try {
          probe.awaitHighlighters(patchHunks)
          body(probe)
        }
        finally {
          trackerJob?.cancelAndJoin()
          rendererJob.cancelAndJoin()
        }
      }
      finally {
        Disposer.dispose(disposable)
        if (!mockEditor) factory.releaseEditor(editor)
      }
    }
  }

  private class Probe(editor: Editor, private val patchHunks: List<Range>, disposable: Disposable) {
    val document: Document = editor.document
    private val changesModel = MutableCodeReviewEditorGutterChangesModel().apply { setChanges(patchHunks.map(Range::asLst)) }
    val model: CodeReviewEditorGutterActionableChangesModel = object : CodeReviewEditorGutterChangesModel by changesModel,
                                                                     CodeReviewEditorGutterActionableChangesModel {
      override var shouldHighlightDiffRanges: Boolean = false
      override fun getBaseContent(lines: LineRange): String? = null
      override fun showDiff(lineIdx: Int?) {}
      override fun addDiffHighlightListener(disposable: Disposable, listener: () -> Unit) {}
    }
    val renderer = ProbeRenderer(model, editor, disposable)
    private val markupModel = DocumentMarkupModel.forDocument(document, null, false) as MarkupModelEx

    fun publish(trackerRanges: List<Range>) {
      setRanges(ExcludingApproximateChangedRangesShifter.shift(patchHunks, trackerRanges))
    }

    fun setRanges(ranges: List<Range>) {
      changesModel.setChanges(ranges.map(Range::asLst))
    }

    fun edit(action: (Document) -> Unit) {
      DocumentUtil.writeInRunUndoTransparentAction { action(document) }
    }

    @Suppress("DEPRECATION")
    suspend fun inBulk(action: suspend () -> Unit) {
      edit { it.setInBulkUpdate(true) }
      try {
        action()
      }
      finally {
        edit { it.setInBulkUpdate(false) }
      }
    }

    fun assertRanges(expected: List<Range>) {
      assertEquals(expected, renderer.visibleRanges()?.map { Range(it.vcsLine1, it.vcsLine2, it.line1, it.line2) })
    }

    suspend fun awaitHighlighters(expected: List<Range>) {
      val expectedOffsets = expected.map { DiffUtil.getLinesRange(document, it.start2, it.end2, false) }
        .map { it.startOffset to it.endOffset }
      withTimeoutOrNull(5.seconds) {
        while (highlighterOffsets() != expectedOffsets) delay(10.milliseconds)
      }
      assertEquals(expectedOffsets, highlighterOffsets())
    }

    private fun highlighterOffsets(): List<Pair<Int, Int>> = markupModel.allHighlighters
      .filter { it.getUserData(LineStatusMarkerRenderer.TOOLTIP_KEY) != null }
      .sortedBy { it.startOffset }
      .map { it.startOffset to it.endOffset }
  }

  private class ProbeRenderer(model: CodeReviewEditorGutterActionableChangesModel, editor: Editor, disposable: Disposable) :
    CodeReviewEditorGutterChangesRenderer(model, editor, disposable) {
    fun visibleRanges(): List<com.intellij.openapi.vcs.ex.Range>? = rangesSource.getRanges()
  }
}

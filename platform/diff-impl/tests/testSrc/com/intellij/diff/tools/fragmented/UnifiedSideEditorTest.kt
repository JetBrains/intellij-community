// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffContext
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.util.Side
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.EditorProjection
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The unified document of the viewer under test shows the lines `a`, `b`, `B`, `c`, `X`, `d` and `e`.
 * The line `b` is on the left side only, the lines `B` and `X` are on the right side only, the other lines are on both sides.
 */
@TestApplication
internal class UnifiedSideEditorTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project
    get() = projectFixture.get()

  private val leftText = "a\nb\nc\nd\ne"
  private val rightText = "a\nB\nc\nX\nd\ne"

  /** The documents as they are, and with a trailing line break, which gives each side an empty last line. */
  private val textPairs = listOf(leftText to rightText, "$leftText\n" to "$rightText\n")

  @Test
  fun `the projection is the editor of the side that shows the line`() = withViewer { viewer ->
    assertResolves(viewer, line = "X", side = Side.RIGHT, offset = rightText.indexOf("X"))
    assertResolves(viewer, line = "b", side = Side.LEFT, offset = leftText.indexOf("b"))
  }

  @Test
  fun `a line of both sides resolves to the master side`() = withViewer { viewer ->
    assertResolves(viewer, line = "c", side = Side.RIGHT, offset = rightText.indexOf("c"))
  }

  @Test
  fun `the nested caret moves the unified caret`() = withViewer { viewer ->
    val left = viewer.projection(Side.LEFT)
    left.caretModel.moveToOffset(leftText.indexOf("d"))
    assertEquals("d", viewer.hostLineAtCaret())
    assertEquals(leftText.indexOf("d"), left.caretModel.offset)
  }

  @Test
  fun `a navigation in the projection lands in the unified editor`() = withViewer { viewer ->
    val left = viewer.projection(Side.LEFT)
    val leftFile = FileDocumentManager.getInstance().getFile(left.document)!!
    OpenFileDescriptor(project, leftFile, leftText.indexOf("e")).navigateIn(left)
    assertEquals("e", viewer.hostLineAtCaret())
  }

  @Test
  fun `a range with a line of the other side inside has no host range`() = withViewer { viewer ->
    val left = viewer.projection(Side.LEFT)
    assertNull(left.toHostRange(TextRange(leftText.indexOf("c"), leftText.indexOf("d") + 1)))

    val right = viewer.projection(Side.RIGHT)
    val hostRange = right.toHostRange(TextRange(rightText.indexOf("c"), rightText.indexOf("d") + 1))
    assertEquals("c\nX\nd", viewer.editor.document.getText(requireNotNull(hostRange)))
  }

  @Test
  fun `a range that ends with a line break stays on its line`() = withViewer { viewer ->
    val left = viewer.projection(Side.LEFT)
    val hostRange = left.toHostRange(TextRange(leftText.indexOf("c"), leftText.indexOf("d")))
    assertEquals("c\n", viewer.editor.document.getText(requireNotNull(hostRange)))
  }

  /**
   * A range is an input from code insight, so it follows the document conventions: an empty range is an offset, and the end is
   * exclusive. The unified text is the oracle: a range maps exactly when the unified text at the mapped start begins with the
   * text of the range, and then both texts are equal, line breaks included.
   */
  @Test
  fun `every range of a side maps exactly when the unified document shows its text in one piece`() {
    for ((leftText, rightText) in textPairs) {
      withViewer(leftText, rightText) { viewer ->
        val hostText = viewer.editor.document.immutableCharSequence
        for (side in Side.entries) {
          val nested = viewer.projection(side)
          val text = nested.document.immutableCharSequence
          for (start in 0..text.length) {
            val hostStart = nested.toHostOffset(start)
            assertTrue(hostStart >= 0, "$side offset $start of '$text' has no host offset")
            for (end in start..text.length) {
              val range = TextRange(start, end)
              val rangeText = range.subSequence(text)
              val shown = hostText.startsWith(rangeText, hostStart)
              val hostRange = nested.toHostRange(range)
              assertEquals(shown, hostRange != null, "$side $range of '$text' maps: ${hostRange != null}, shown in one piece: $shown")
              if (hostRange != null) {
                assertEquals(hostStart, hostRange.startOffset, "start of $side $range of '$text'")
                assertEquals(rangeText.toString(), hostRange.subSequence(hostText).toString(), "text of $side $range of '$text'")
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun `every offset of a side round-trips through the host`() {
    for ((leftText, rightText) in textPairs) {
      withViewer(leftText, rightText) { viewer ->
        for (side in Side.entries) {
          val nested = viewer.projection(side)
          for (offset in 0..nested.document.textLength) {
            assertEquals(offset, nested.fromHostOffset(nested.toHostOffset(offset)), "$side offset $offset of '$leftText' / '$rightText'")
          }
        }
      }
    }
  }

  /**
   * A comparison that ignores whitespace shows a shared line with the text of the master side, the right side here.
   * A column of the left side then follows the words, not the count of characters.
   */
  @Test
  fun `a column of the other side follows the words when the comparison ignores whitespace`() {
    val settings = TextDiffSettings()
    settings.ignorePolicy = IgnorePolicy.IGNORE_WHITESPACES
    val indented = "a\n   my text\nc"
    val spaced = "a\nmy     text\nc"
    withViewer(indented, spaced, settings) { viewer ->
      val hostText = viewer.editor.document.text
      assertTrue(hostText.contains("my     text"), "the unified document shows the master side: '$hostText'")

      val left = viewer.projection(Side.LEFT)
      assertEquals(hostText.indexOf("my"), left.toHostOffset(indented.indexOf("my")))
      assertEquals(hostText.indexOf("my"), left.toHostOffset(indented.indexOf("my") - 2), "inside the indent")
      assertEquals(hostText.indexOf("text"), left.toHostOffset(indented.indexOf("text")))
      assertEquals(indented.indexOf("text"), left.fromHostOffset(hostText.indexOf("text")))
      val word = TextRange(indented.indexOf("text"), indented.indexOf("text") + 4)
      assertEquals("text", viewer.editor.document.getText(requireNotNull(left.toHostRange(word))))
      val lineWithBreak = TextRange(indented.indexOf("my"), indented.indexOf("\nc") + 1)
      assertEquals("my     text\n", viewer.editor.document.getText(requireNotNull(left.toHostRange(lineWithBreak))))

      val right = viewer.projection(Side.RIGHT)
      assertEquals(hostText.indexOf("text"), right.toHostOffset(spaced.indexOf("text")))
    }
  }

  @Test
  fun `the data context navigates in the projection of the caret side`() = withViewer { viewer ->
    viewer.editor.caretModel.moveToOffset(viewer.hostOffsetOfLine("X"))
    // the headless data manager of a test does not walk the component tree, so the snapshot of the viewer is read directly
    val context = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink -> viewer.uiDataSnapshot(sink) }
    val editor = context.getData(OpenFileDescriptor.NAVIGATE_IN_EDITOR)
    assertSame(viewer.projection(Side.RIGHT), editor)
  }

  /**
   * The caret of the unified editor expands a collapsed block on its own when it lands inside the block.
   * The first line of the block is the case that needs the folding model of the editor the navigation lands in.
   */
  @Test
  fun `a navigation to the first line of a collapsed block expands it`() {
    val settings = TextDiffSettings()
    settings.isExpandByDefault = false
    settings.contextRange = 1
    val unchangedTail = (1..12).joinToString("\n") { "l$it" }
    withViewer(leftText = "a\n$unchangedTail", rightText = "A\n$unchangedTail", settings = settings) { viewer ->
      // the viewer nests several collapsed regions in one block, so the target is the first collapsed line
      val hostDocument = viewer.editor.document
      val blockStart = viewer.editor.foldingModel.allFoldRegions.filter { !it.isExpanded }.minOf { it.startOffset }
      val line = hostDocument.getLineNumber(blockStart)
      val lineRange = TextRange(hostDocument.getLineStartOffset(line), hostDocument.getLineEndOffset(line))

      val right = viewer.projection(Side.RIGHT)
      val rightFile = FileDocumentManager.getInstance().getFile(right.document)!!
      OpenFileDescriptor(project, rightFile, right.fromHostOffset(blockStart)).navigateIn(right)

      val collapsed = viewer.editor.foldingModel.allFoldRegions.filter { !it.isExpanded && lineRange.intersects(it) }
      assertTrue(collapsed.isEmpty(), "the target line stayed collapsed: $collapsed")
      assertEquals(blockStart, viewer.editor.caretModel.offset)
    }
  }

  private fun assertResolves(viewer: UnifiedDiffViewer, line: String, side: Side, offset: Int) {
    val hostOffset = viewer.hostOffsetOfLine(line)
    viewer.editor.caretModel.moveToOffset(hostOffset)
    val editor = EditorProjection.resolveAt(viewer.editor, hostOffset)
    assertSame(viewer.projection(side), editor, "side of the line '$line'")
    assertEquals(offset, editor.caretModel.offset, "caret of the line '$line'")
    assertEquals(hostOffset, (editor as EditorProjection).toHostOffset(offset), "host offset of the line '$line'")
  }

  private fun UnifiedDiffViewer.projection(side: Side): EditorProjection {
    val editor = highlightEditors.filterIsInstance<EditorProjection>().single { it.document == getDocument(side) }
    assertFalse(editor.isDisposed)
    return editor
  }

  private fun UnifiedDiffViewer.hostOffsetOfLine(text: String): Int {
    val document = editor.document
    val line = (0 until document.lineCount).single { lineText(it) == text }
    return document.getLineStartOffset(line)
  }

  private fun UnifiedDiffViewer.hostLineAtCaret(): String = lineText(editor.document.getLineNumber(editor.caretModel.offset))

  private fun UnifiedDiffViewer.lineText(line: Int): String {
    val document = editor.document
    return document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
  }

  private fun withViewer(
    leftText: String = this.leftText,
    rightText: String = this.rightText,
    settings: TextDiffSettings = TextDiffSettings(),
    block: (UnifiedDiffViewer) -> Unit,
  ) {
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      writeIntentReadAction {
        val contentFactory = DiffContentFactoryImpl()
        val leftContent: DocumentContent = contentFactory.create(project, leftText, PlainTextFileType.INSTANCE)
        val rightContent: DocumentContent = contentFactory.create(project, rightText, PlainTextFileType.INSTANCE)
        val context = TestDiffContext(project)
        context.putUserData(TextDiffSettings.KEY, settings)

        val viewer = UnifiedDiffViewer(context, SimpleDiffRequest(null, leftContent, rightContent, null, null))
        try {
          viewer.init()
          // on the EDT a write lock makes DiffViewerBase.forceRediffSynchronously() true, so this rediff runs and finishes inline
          runWriteAction { viewer.rediff(true) }
          assertFalse(viewer.hasPendingRediff(), "the rediff did not finish synchronously")
          block(viewer)
        }
        finally {
          Disposer.dispose(viewer)
        }
      }
    }
  }

  private class TestDiffContext(private val project: Project) : DiffContext() {
    override fun getProject(): Project = project
    override fun isWindowFocused(): Boolean = true
    override fun isFocusedInWindow(): Boolean = true
    override fun requestFocusInWindow() = Unit
  }
}

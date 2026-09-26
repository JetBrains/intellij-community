// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffContext
import com.intellij.diff.HeavyDiffTestCase
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.util.Side
import com.intellij.idea.TestFor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.withForcedRespectIsShowingClientProperty
import com.intellij.util.ui.withShowingChanged
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Editing in an aligned side-by-side diff must not move the viewport away from the caret.
 *
 * The content is the reduced repro from the issue: a tall one-sided insertion near the top, which
 * gives the left editor a block of alignment inlays 28 lines high, plus a five-lines-to-one-line
 * modification on the last line, where the caret sits.
 *
 * Note that these tests pin the geometry and the invariants, but they are not a regression guard
 * for the original report: the permanent jump needs a layout pass that a headless editor does not
 * do, so `test typing on the last line` passes on the unfixed code too. Only
 * [test queued realign keeps the scroll position] fails without the fix.
 */
@TestFor(issues = ["IJPL-101212"])
class SimpleDiffViewerScrollTest : HeavyDiffTestCase() {

  private val visibleLines = 26

  fun `test typing on the last line keeps the caret visible`() {
    doTestTyping(currentSide = Side.RIGHT)
  }

  /**
   * The same edit while the *left* editor is the current side, which is the path
   * `SimpleDiffViewer.runPreservingScrollingPosition` picks its single keeper from.
   */
  fun `test typing on the last line keeps the caret visible with left as current side`() {
    doTestTyping(currentSide = Side.LEFT)
  }

  private fun doTestTyping(currentSide: Side) {
    withViewer { viewer ->
      viewer.currentSide = currentSide

      val rightEditor = viewer.getEditor(Side.RIGHT)
      val leftEditor = viewer.getEditor(Side.LEFT)

      val lastLine = rightEditor.document.lineCount - 1
      parkCaretAtBottom(rightEditor, lastLine)
      assertTrue("caret should start out visible", isCaretVisible(rightEditor))
      assertAligned(viewer, "before typing")

      val offset = rightEditor.document.getLineEndOffset(lastLine)
      WriteCommandAction.runWriteCommandAction(project) {
        rightEditor.document.insertString(offset, "d")
      }
      viewer.rediff(true)
      waitForRediff(viewer)

      assertTrue("typing scrolled the caret line off screen, ${describe(rightEditor)}", isCaretVisible(rightEditor))
      assertAligned(viewer, "after typing")
      assertFalse("the left editor must not be left at the top of the file, ${describe(leftEditor)}",
                  leftEditor.scrollingModel.verticalScrollOffset == 0)
    }
  }

  /**
   * `AlignedDiffModelBase.realignChanges()` drops every alignment inlay and recreates it. While
   * they are gone the documents are shorter by the total height of the alignment blocks, which
   * clamps the vertical scroll offset. Scheduled from the model's own queue it runs outside
   * `SimpleDiffViewer.runPreservingScrollingPosition`, so nothing else restores it.
   */
  fun `test queued realign keeps the scroll position`() {
    // The realign queue only runs while the viewer component shows - see
    // DebouncedUpdates.forComponent(). A headless test has no window, so the component is marked as
    // showing by hand, which `launchOnShow` reads only while this flag is on.
    withForcedRespectIsShowingClientProperty {
      doTestQueuedRealign()
    }
  }

  private fun doTestQueuedRealign() {
    withViewer { viewer ->
      markAsShowing(viewer.component)

      val rightEditor = viewer.getEditor(Side.RIGHT)
      val leftEditor = viewer.getEditor(Side.LEFT)

      parkCaretAtBottom(rightEditor, rightEditor.document.lineCount - 1)
      val rightBefore = rightEditor.scrollingModel.verticalScrollOffset
      val leftBefore = leftEditor.scrollingModel.verticalScrollOffset
      assertTrue("expected to be scrolled away from the top", rightBefore > 0)

      // The alignment inlays are rebuilt as new instances, so identity tells us the realign ran.
      val alignInlaysBefore = blockInlays(leftEditor)

      // Any inlay change schedules a realign. Add it past the end of the viewport so that the
      // addition itself cannot move the viewport - only the rebuild can.
      rightEditor.inlayModel.addBlockElement(
        rightEditor.document.textLength,
        InlayProperties(),
        object : EditorCustomElementRenderer {
          override fun calcWidthInPixels(inlay: Inlay<*>): Int = 10
        }
      )

      // The queue debounces for 300ms and runs on the EDT, so the events have to be pumped until
      // the rebuild actually happened.
      PlatformTestUtil.waitWithEventsDispatching(
        "realignChanges() did not run", { blockInlays(leftEditor) != alignInlaysBefore }, 10
      )

      assertEquals("queued realign moved the right viewport", rightBefore,
                   rightEditor.scrollingModel.verticalScrollOffset)
      assertEquals("queued realign moved the left viewport", leftBefore,
                   leftEditor.scrollingModel.verticalScrollOffset)
    }
  }

  /**
   * Puts [component] under a parent that carries the `Component.isShowing` client property, which is
   * what `ComponentUtil.isShowing` looks for when the component has no window. The addition fires a
   * `HierarchyEvent`, and [withShowingChanged] makes `launchOnShow` read it as a showing change.
   *
   * The caller must hold [withForcedRespectIsShowingClientProperty], or `launchOnShow` asks Swing
   * instead and still sees a component that does not show.
   */
  private fun markAsShowing(component: JComponent) {
    val container = JPanel()
    ComponentUtil.forceMarkAsShowing(container, true)
    withShowingChanged { container.add(component) }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun blockInlays(editor: EditorEx): List<Inlay<*>> =
    editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength)

  private fun parkCaretAtBottom(editor: EditorEx, line: Int) {
    editor.caretModel.moveToLogicalPosition(LogicalPosition(line, 0))
    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun isCaretVisible(editor: EditorEx): Boolean {
    val visible = editor.scrollingModel.visibleArea
    val caretY = editor.visualLineToY(editor.caretModel.visualPosition.line)
    return caretY >= visible.y && caretY + editor.lineHeight <= visible.y + visible.height
  }

  /**
   * Aligned mode pairs the two vertical offsets, see `MySyncScrollable.forceSyncVerticalScroll`.
   * With no editor headers in play the pairing is plain equality.
   */
  private fun assertAligned(viewer: SimpleDiffViewer, phase: String) {
    val left = viewer.getEditor(Side.LEFT).scrollingModel.verticalScrollOffset
    val right = viewer.getEditor(Side.RIGHT).scrollingModel.verticalScrollOffset
    assertEquals("editors are not aligned $phase", left, right)
  }

  private fun describe(editor: EditorEx): String {
    val visible = editor.scrollingModel.visibleArea
    return "caretY=${editor.visualLineToY(editor.caretModel.visualPosition.line)}, visibleArea=$visible"
  }

  private fun withViewer(body: (SimpleDiffViewer) -> Unit) {
    val dir = Path.of(PlatformTestUtil.getCommunityPath(), "platform/diff-impl/tests/testData/diff/scroll")
    val contentFactory = DiffContentFactoryImpl()
    val leftContent: DocumentContent = contentFactory.create(project, dir.resolve("ijpl101212_before.md").toFile().readText())
    val rightContent: DocumentContent = contentFactory.create(project, dir.resolve("ijpl101212_after.md").toFile().readText())
    rightContent.document.setReadOnly(false)

    val context = MockDiffContext(project)
    // Mirror the commit diff: unchanged ranges collapsed, changes aligned.
    val settings = TextDiffSettings()
    settings.isExpandByDefault = false
    settings.contextRange = 4
    settings.isEnableAligningChangesMode = true
    context.putUserData(TextDiffSettings.KEY, settings)

    val viewer = SimpleDiffViewer(context, SimpleDiffRequest(null, leftContent, rightContent, null, null))
    try {
      viewer.init()
      // Give the editors their size before the first rediff, so the initial fold install and
      // realign already run against a real viewport.
      for (side in listOf(Side.LEFT, Side.RIGHT)) {
        EditorTestUtil.setEditorVisibleSize(viewer.getEditor(side), 100, visibleLines)
      }
      viewer.rediff(true)
      waitForRediff(viewer)
      UIUtil.dispatchAllInvocationEvents()

      body(viewer)
    }
    finally {
      Disposer.dispose(viewer)
    }
  }

  private fun waitForRediff(viewer: SimpleDiffViewer) {
    PlatformTestUtil.waitWithEventsDispatching("Rediff did not finish in time", { !viewer.hasPendingRediff() }, 10)
    UIUtil.dispatchAllInvocationEvents()
  }

  private class MockDiffContext(private val myProject: Project?) : DiffContext() {
    override fun getProject(): Project? = myProject
    override fun isWindowFocused(): Boolean = true
    override fun isFocusedInWindow(): Boolean = true
    override fun requestFocusInWindow() {}
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffContext
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.util.Side
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The divider must not paint a damaged change.
 *
 * A damaged change keeps its old line numbers until the next rediff. When the document gets shorter, these lines can point past
 * its end, and the paint fails with an `IndexOutOfBoundsException`.
 *
 * The tests edit the document through the viewer's own document listener, and never call `rediff`. A headless component does not
 * show, so `DiffViewerBase.scheduleRediff` does not arm the alarm. That is the same window a user gets between an edit and the
 * background rediff.
 *
 * The whole viewer lifecycle stays inside one [Dispatchers.EDT] block, because it creates, mutates and disposes editors.
 */
@TestApplication
class SimpleDiffViewerStaleChangeTest {

  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project get() = projectFixture.get()

  private val visibleLines = 60
  private val dividerWidth = 40
  private val dividerHeight = 4000

  private val leftText: String = (0..39).joinToString("\n") { "line %02d".format(it) }

  /** Two changes: a four-line block at 30..33, and a single line at 37. */
  private val rightText: String = (0..39).joinToString("\n") {
    if (it in 30..33 || it == 37) "CHANGED %02d".format(it) else "line %02d".format(it)
  }

  @BeforeEach
  fun enableDiffVerification() {
    DiffIterableUtil.setVerifyEnabled(true)
  }

  @AfterEach
  fun disableDiffVerification() {
    DiffIterableUtil.setVerifyEnabled(false)
  }

  @Test
  @Timeout(30)
  fun `the divider paints after the document gets shorter`(): Unit = timeoutRunBlocking {
    withViewer { viewer ->
      val document = viewer.getEditor(Side.RIGHT).document
      assertEquals(2, viewer.diffChanges.size, "the initial diff must hold two changes")

      // Straddles the start of the change at 30..33, so the change is damaged, not merely shifted.
      deleteLines(document, 28, 31)
      assertOneChangeDamaged(viewer)

      // Shortens the document above both changes.
      deleteLines(document, 0, 12)

      paintDivider(viewer)
    }
  }

  /**
   * A change holds the lines of both documents, and a damage on one side drops it from the lists of both sides.
   * A later modification of the *other* side must not make the divider paint it.
   */
  @Test
  @Timeout(30)
  fun `the divider paints after the other document gets shorter`(): Unit = timeoutRunBlocking {
    withViewer(editableLeft = true) { viewer ->
      deleteLines(viewer.getEditor(Side.RIGHT).document, 28, 31)
      assertOneChangeDamaged(viewer)

      deleteLines(viewer.getEditor(Side.LEFT).document, 0, 12)

      paintDivider(viewer)
    }
  }

  /**
   * A bulk modification still reaches the model, because the viewer installs a plain [com.intellij.openapi.editor.event.DocumentListener].
   * Only a [com.intellij.openapi.editor.event.BulkAwareDocumentListener] opts out of the events of a bulk mode.
   */
  @Test
  @Timeout(30)
  fun `the divider paints after a bulk modification`(): Unit = timeoutRunBlocking {
    withViewer { viewer ->
      val document = viewer.getEditor(Side.RIGHT).document
      deleteLines(document, 28, 31)
      assertOneChangeDamaged(viewer)

      WriteCommandAction.runWriteCommandAction(project) {
        DocumentUtil.executeInBulk(document) {
          document.deleteString(document.getLineStartOffset(0), document.getLineStartOffset(12))
        }
      }
      UIUtil.dispatchAllInvocationEvents()

      paintDivider(viewer)
    }
  }

  private fun assertOneChangeDamaged(viewer: TestSimpleDiffViewer) {
    assertEquals(1, viewer.diffChanges.size, "the deletion had to damage exactly one change")
    assertEquals(1, viewer.diffModel.allChanges.count { !it.isValid }, "exactly one change had to be damaged")
  }

  private fun paintDivider(viewer: TestSimpleDiffViewer) {
    val image = BufferedImage(dividerWidth, dividerHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    // paintPolygons reads the clip to find the painted interval, and a new Graphics2D has none.
    graphics.clipRect(0, 0, dividerWidth, dividerHeight)
    try {
      viewer.diffModel.paintPolygons(graphics, divider())
    }
    finally {
      graphics.dispose()
    }
  }

  private fun deleteLines(document: Document, fromLine: Int, toLine: Int) {
    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(document.getLineStartOffset(fromLine), document.getLineStartOffset(toLine))
    }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun divider(): JComponent = JPanel().also { it.setSize(dividerWidth, dividerHeight) }

  private suspend fun withViewer(editableLeft: Boolean = false, body: (TestSimpleDiffViewer) -> Unit) {
    withContext(Dispatchers.EDT) {
      val contentFactory = DiffContentFactoryImpl()
      val leftContent: DocumentContent = contentFactory.create(project, leftText)
      val rightContent: DocumentContent = contentFactory.create(project, rightText)
      rightContent.document.setReadOnly(false)
      if (editableLeft) leftContent.document.setReadOnly(false)

      val context = MockDiffContext(project)
      val settings = TextDiffSettings()
      // No folding, so that the painted interval covers the whole file.
      settings.isExpandByDefault = true
      context.putUserData(TextDiffSettings.KEY, settings)

      val viewer = TestSimpleDiffViewer(context, SimpleDiffRequest(null, leftContent, rightContent, null, null))
      try {
        viewer.init()
        for (side in listOf(Side.LEFT, Side.RIGHT)) {
          EditorTestUtil.setEditorVisibleSize(viewer.getEditor(side), 120, visibleLines)
        }
        viewer.rediff(true)
        PlatformTestUtil.waitWithEventsDispatching("Rediff did not finish in time", { !viewer.hasPendingRediff() }, 10)
        UIUtil.dispatchAllInvocationEvents()

        body(viewer)
      }
      finally {
        Disposer.dispose(viewer)
      }
    }
  }

  /** [SimpleDiffViewer.getDiffChanges] reports the valid changes only, and these tests need the damaged ones too. */
  private class TestSimpleDiffViewer(context: DiffContext, request: ContentDiffRequest) : SimpleDiffViewer(context, request) {
    val diffModel: SimpleDiffModel get() = myModel
  }

  private class MockDiffContext(private val myProject: Project?) : DiffContext() {
    override fun getProject(): Project? = myProject
    override fun isWindowFocused(): Boolean = true
    override fun isFocusedInWindow(): Boolean = true
    override fun requestFocusInWindow() {}
  }
}

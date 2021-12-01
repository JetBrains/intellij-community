/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import org.jetbrains.plugins.notebooks.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.*
import org.jetbrains.plugins.notebooks.visualization.r.ui.UiCustomizer
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Graphics
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

class NotebookInlayComponentPsi(val cell: PsiElement, editor: EditorImpl) : NotebookInlayComponent(editor) {
  override fun updateCellSeparator() {
    if (!UiCustomizer.instance.showUpdateCellSeparator) {
      return
    }

    if (separatorHighlighter != null &&
        separatorHighlighter!!.startOffset == cell.textRange.startOffset &&
        separatorHighlighter!!.endOffset == cell.textRange.endOffset) {
      return
    }

    if (separatorHighlighter != null) {
      editor.markupModel.removeHighlighter(separatorHighlighter!!)
    }

    // ToDo This is half-hack. The problem is that updateCellSeparator called from PSI change
    // but if we have a lot of sequential changes in editor.document (line Backspace button is hold)
    // document was updated but PSI update is async and that's why we have this check.
    if (cell.textRange.endOffset > editor.document.textLength) {
      return
    }

    try {
      separatorHighlighter = createSeparatorHighlighter(editor, cell.textRange)
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }
}

class NotebookInlayComponentInterval(val cell: NotebookIntervalPointer, editor: EditorImpl) : NotebookInlayComponent(editor) {
  override fun updateCellSeparator() {
    if (!UiCustomizer.instance.showUpdateCellSeparator) {
      return
    }

    if (separatorHighlighter != null) {
      editor.markupModel.removeHighlighter(separatorHighlighter!!)
    }

    try {
      val interval = cell.get() ?: return
      val doc = editor.document
      val textRange = TextRange(doc.getLineStartOffset(interval.lines.first), doc.getLineEndOffset(interval.lines.last))
      separatorHighlighter = createSeparatorHighlighter(editor, textRange)
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }
}

private fun createSeparatorHighlighter(editor: EditorImpl, textRange: TextRange) =
  editor.markupModel.addRangeHighlighter(textRange.startOffset, textRange.endOffset,
                                         HighlighterLayer.SYNTAX - 1, null,
                                         HighlighterTargetArea.LINES_IN_RANGE).apply {

    customRenderer = NotebookInlayComponent.separatorRenderer
    lineMarkerRenderer = LineMarkerRenderer { _, g, r ->
      @Suppress("INACCESSIBLE_TYPE")
      val gutterWidth = ((editor as EditorEx).gutterComponentEx as JComponent).width

      val y = r.y + r.height - editor.lineHeight
      g.color = editor.colorsScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR)
      g.drawLine(0, y, gutterWidth + 10, y)
    }
  }

abstract class NotebookInlayComponent(protected val editor: EditorImpl)
  : InlayComponent(), MouseListener, MouseMotionListener {
  companion object {
    val separatorRenderer = CustomHighlighterRenderer { editor, highlighter1, g ->
      val y1 = editor.offsetToPoint2D(highlighter1.endOffset).y.toInt()

      g.color = editor.colorsScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR)

      g.drawLine(0, y1, editor.component.width, y1)
    }
  }

  lateinit var beforeHeightChanged: () -> Unit
  lateinit var afterHeightChanged: () -> Unit
  var selected = false

  private var state: NotebookInlayState? = null

  private var expandedHeight = 0

  /** Inlay short view, shown in collapsed state or in empty state. */
  private var toolbar: NotebookInlayToolbar? = null

  private var gutter: JComponent? = null

  private var mouseOverNewParagraphArea = false

  protected var separatorHighlighter: RangeHighlighter? = null

  private val disposable = Disposer.newDisposable()

  /** If the value is `false`, the component won't limit its height and apply the height passed to [adjustSize] method directly. */
  private var shouldLimitMaxHeight = true

  init {
    cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    border = JBUI.Borders.empty(InlayDimensions.topBorderUnscaled,
                                InlayDimensions.leftBorderUnscaled,
                                InlayDimensions.bottomBorderUnscaled,
                                InlayDimensions.rightBorderUnscaled)
    Disposer.register(editor.disposable, disposable)
    addMouseListener(this)
    addMouseMotionListener(this)
  }

  // region MouseListener
  override fun mouseReleased(e: MouseEvent?) {}

  override fun mouseEntered(e: MouseEvent?) {}

  override fun mouseExited(e: MouseEvent?) {
    if (mouseOverNewParagraphArea) {
      mouseOverNewParagraphArea = false
      repaint(0, height - JBUI.scale(20), width, height)
    }
  }

  override fun mousePressed(e: MouseEvent?) {}
  override fun mouseClicked(e: MouseEvent?) {}

  // endregion

  // region MouseMotionListener
  override fun mouseMoved(e: MouseEvent) {
    val value = e.y > height - JBUI.scale(20)
    if (value != mouseOverNewParagraphArea) {
      mouseOverNewParagraphArea = value
      repaint(0, height - JBUI.scale(20), width, height)
    }
  }

  override fun mouseDragged(e: MouseEvent?) {}
  // endregion

  private fun getOrAddToolbar(): NotebookInlayToolbar {
    toolbar?.let { return it }
    return NotebookInlayToolbar().also {
      toolbar = it
      add(it, BorderLayout.CENTER)
    }
  }

  private fun removeToolbar() {
    toolbar?.let { remove(it) }
    toolbar = null
  }

  override fun paintComponent(g: Graphics) {
    /** Paints rounded rect panel - background of inlay component. */
    val g2d = g.create()

    g2d.color = //if (selected) {
      //inlay!!.editor.colorsScheme.getAttributes(RMARKDOWN_CHUNK).backgroundColor
      //}
      //else {
      (inlay!!.editor as EditorImpl).let {
        it.notebookAppearance.getInlayBackgroundColor(it.colorsScheme) ?: it.backgroundColor
      }
    //}

    g2d.fillRect(0, 0, width, InlayDimensions.topOffset + InlayDimensions.cornerRadius)
    g2d.fillRect(0, height - InlayDimensions.bottomOffset - InlayDimensions.cornerRadius, width,
                 InlayDimensions.bottomOffset + InlayDimensions.cornerRadius)


    g2d.color = UIUtil.getLabelBackground()
    g2d.fillRoundRect(0, InlayDimensions.topOffset, width,
                      height - InlayDimensions.bottomOffset - InlayDimensions.topOffset,
                      InlayDimensions.cornerRadius, InlayDimensions.cornerRadius)

    g2d.dispose()
  }

  /**
   * Draw separator line below cell. Also fills cell background
   */
  protected abstract fun updateCellSeparator()

  override fun assignInlay(inlay: Inlay<*>) {
    super.assignInlay(inlay)

    updateCellSeparator()

    gutter = editor.gutter as JComponent
  }

  override fun disposeInlay() {
    if (separatorHighlighter != null && inlay != null) {
      editor.markupModel.removeHighlighter(separatorHighlighter!!)
      separatorHighlighter = null
    }
    super.disposeInlay()
  }

  fun dispose() {
    Disposer.dispose(disposable)
  }

  /** Changes size of component  and also called updateSize for inlay. */
  override fun deltaSize(dx: Int, dy: Int) {
    if (dx == 0 && dy == 0) {
      return
    }

    val newWidth = max(size.width + dx, InlayDimensions.minWidth)
    var newHeight = size.height + dy

    newHeight = max(InlayDimensions.minHeight, newHeight)
    val newDx = newWidth - size.width
    val newDy = newHeight - size.height

    super.deltaSize(newDx, newDy)
  }

  /** Creates a component for displaying output. */
  private fun getOrCreateOutput(): NotebookInlayOutput {

    if (state !is NotebookInlayOutput) {

      if (state != null) {
        remove(state)
      }

      state = NotebookInlayOutput(editor, disposable).apply {
        addToolbar()
        onHeightCalculated = { height ->
          ApplicationManager.getApplication().invokeLater {
            adjustSize(height)
          }
        }
      }.also { addState(it) }
    }
    resizable = true
    return state as NotebookInlayOutput
  }

  fun updateProgressStatus(progressStatus: InlayProgressStatus) {
    state?.updateProgressStatus(progressStatus)
  }

  private fun addState(state: NotebookInlayState) {
    add(state, BorderLayout.CENTER)

    // Resizing only if inlay has minimal size or if we have expanded size set.
    if (expandedHeight != 0) {
      deltaSize(0, expandedHeight - size.height)
      expandedHeight = 0
    }
    else if (height < InlayDimensions.minHeight) {
      deltaSize(0, InlayDimensions.defaultHeight - height)
    }

    revalidate()
    repaint()
  }

  /** Adjusts size of notebook output. Method called when success data comes with inlay component desired height. */
  private fun adjustSize(height: Int) {
    beforeHeightChanged()

    var desiredHeight = height + InlayDimensions.topBorder + InlayDimensions.bottomBorder
    if (shouldLimitMaxHeight) {
      desiredHeight = min(InlayDimensions.defaultHeight, desiredHeight)
    }

    deltaSize(0, desiredHeight - size.height)

    afterHeightChanged()
  }

  /** Event from notebook with console output. This output contains intermediate data from console. */
  private fun onOutput(data: String, type: String, progressStatus: InlayProgressStatus?, cleanup: () -> Unit) {
    state?.clear()

    val output = getOrCreateOutput()
    output.clearAction = cleanup
    if (output.addData(type, data, progressStatus)?.shouldLimitHeight() == false) {
      shouldLimitMaxHeight = false
    }

    InlayStateCustomizer.EP.extensionList
      .filter { it.isApplicableTo(output::class.java) }
      .forEach { customizer -> customizer.customize(output) }

    if (UiCustomizer.instance.isResizeOutputToPreviewHeight && size.height == InlayDimensions.smallHeight) {
      deltaSize(0, InlayDimensions.previewHeight - size.height)
    }
  }

  private fun onMultiOutput(inlayOutputs: List<InlayOutput>, cleanup: () -> Unit) {
    state?.clear()
    removeToolbar()

    shouldLimitMaxHeight = false

    if (state !is NotebookInlayMultiOutput) {
      if (state != null) {
        remove(state)
      }
      state = MultiOutputProvider.EP.extensionList.firstOrNull()?.create(editor, disposable) ?: TabbedMultiOutput(editor, disposable)
      state?.apply {
        onHeightCalculated = { height ->
          ApplicationManager.getApplication().invokeLater {
            adjustSize(height)
          }
        }
        clearAction = cleanup
      }?.also { addState(it) }
    }
    resizable = true
    (state as? NotebookInlayMultiOutput)?.let { st ->
      st.onOutputs(inlayOutputs)
      InlayStateCustomizer.EP.extensionList
        .filter { it.isApplicableTo(st::class.java) }
        .forEach { customizer -> customizer.customize(st) }
    }
  }

  fun addInlayOutputs(inlayOutputs: List<InlayOutput>, cleanup: () -> Unit) {
    if (inlayOutputs.size > 1) {
      onMultiOutput(inlayOutputs, cleanup)
    }
    else {
      val inlay = inlayOutputs.first()
      onOutput(inlay.data, inlay.type, inlay.progressStatus, cleanup)
    }
  }

  fun addText(message: String, outputType: Key<*>) {
    getOrCreateOutput().addText(message, outputType)
    if (size.height <= InlayDimensions.previewHeight * 3) {
      deltaSize(0, min(InlayDimensions.lineHeight * (message.lines().size - 1), InlayDimensions.previewHeight * 3 - size.height))
    }
  }

  fun createOutputComponent() {
    getOrCreateOutput().addText("", ProcessOutputType.STDOUT)
  }

  fun onViewportChange(isInViewport: Boolean) {
    state?.onViewportChange(isInViewport)
  }

  fun clearOutputs() {
    state?.clear()
    removeToolbar()
  }
}

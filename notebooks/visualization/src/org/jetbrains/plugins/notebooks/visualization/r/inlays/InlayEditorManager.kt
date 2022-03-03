/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.FutureResult
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.InlayProgressStatus
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.Future
import kotlin.math.max
import kotlin.math.min

/**
 * Manages inlays.
 *
 * On project load subscribes
 *    on editor opening/closing.
 *    on adding/removing notebook cells
 *    on any document changes
 *    on folding actions
 *
 * On editor open checks the PSI structure and restores saved inlays.
 *
 * ToDo should be split into InlaysManager with all basics and NotebookInlaysManager with all specific.
 */
class EditorInlaysManager(val project: Project, private val editor: EditorImpl, val descriptor: InlayElementDescriptor) {

  private val inlays: MutableMap<PsiElement, NotebookInlayComponentPsi> = LinkedHashMap()
  private val inlayElements = LinkedHashSet<PsiElement>()
  private val scrollKeeper: EditorScrollingPositionKeeper = EditorScrollingPositionKeeper(editor)
  private val viewportQueue = MergingUpdateQueue(VIEWPORT_TASK_NAME, VIEWPORT_TIME_SPAN, true, null, project)
  @Volatile private var toolbarUpdateScheduled: Boolean = false

  init {
    addResizeListener()
    addCaretListener()
    addFoldingListener()
    addDocumentListener()
    addViewportListener()
    editor.settings.isRightMarginShown = false
    UISettings.instance.showEditorToolTip = false
    MouseWheelUtils.wrapEditorMouseWheelListeners(editor)
    restoreToolbars().onSuccess { restoreOutputs() }
    onCaretPositionChanged()
    ApplicationManager.getApplication().invokeLater {
      if (editor.isDisposed) return@invokeLater
      updateInlayComponentsWidth()
    }
  }

  fun dispose() {
    inlays.values.forEach {
      it.disposeInlay()
      it.dispose()
    }
    inlays.clear()
    inlayElements.clear()
  }

  fun updateCell(psi: PsiElement, inlayOutputs: List<InlayOutput>? = null, createTextOutput: Boolean = false): Future<Unit> {
    val result = FutureResult<Unit>()
    if (ApplicationManager.getApplication().isUnitTestMode && !isEnabledInTests) return result.apply { set(Unit) }
    ApplicationManager.getApplication().invokeLater {
      try {
        if (editor.isDisposed) {
          result.set(Unit)
          return@invokeLater
        }
        if (!psi.isValid) {
          getInlayComponent(psi)?.let { oldInlay -> removeInlay(oldInlay, cleanup = false) }
          result.set(Unit)
          return@invokeLater
        }
        if (isOutputPositionCollapsed(psi)) {
          result.set(Unit)
          return@invokeLater
        }
        val outputs = inlayOutputs ?: descriptor.getInlayOutputs(psi)
        if (outputs == null) {
          result.set(Unit)
          return@invokeLater
        }
        scrollKeeper.savePosition()
        getInlayComponent(psi)?.let { oldInlay -> removeInlay(oldInlay, cleanup = false) }
        if (outputs.isEmpty() && !createTextOutput) {
          result.set(Unit)
          return@invokeLater
        }
        val component = addInlayComponent(psi)
        if (outputs.isNotEmpty()) addInlayOutputs(component, outputs)
        if (createTextOutput) component.createOutputComponent()
        scrollKeeper.restorePosition(true)
      } catch (e: Throwable) {
        result.set(Unit)
        throw e
      }
      ApplicationManager.getApplication().invokeLater {
        try {
          scrollKeeper.savePosition()
          updateInlays()
          scrollKeeper.restorePosition(true)
        }
        finally {
          result.set(Unit)
        }
      }
    }
    return result
  }

  private fun isOutputPositionCollapsed(psiCell: PsiElement): Boolean =
    editor.foldingModel.isOffsetCollapsed(descriptor.getInlayOffset(psiCell))

  fun addTextToInlay(psi: PsiElement, message: String, outputType: Key<*>) {
    invokeLater {
      scrollKeeper.savePosition()
      getInlayComponent(psi)?.addText(message, outputType)
      scrollKeeper.restorePosition(true)
    }
  }

  fun updateInlayProgressStatus(psi: PsiElement, progressStatus: InlayProgressStatus): Future<Unit> {
    val result = FutureResult<Unit>()
    ApplicationManager.getApplication().invokeLater {
      getInlayComponent(psi)?.updateProgressStatus(progressStatus)
      result.set(Unit)
    }
    return result
  }

  private fun updateInlaysForViewport() {
    invokeLater {
      if (editor.isDisposed) return@invokeLater
      val viewportRange = calculateViewportRange(editor)
      val expansionRange = calculateInlayExpansionRange(editor, viewportRange)
      for (element in inlayElements) {
        updateInlayForViewport(element, viewportRange, expansionRange)
      }
    }
  }

  private fun updateInlayForViewport(element: PsiElement, viewportRange: IntRange, expansionRange: IntRange) {
    val inlay = inlays[element]
    if (inlay != null) {
      val bounds = inlay.bounds
      val isInViewport = bounds.y <= viewportRange.last && bounds.y + bounds.height >= viewportRange.first
      inlay.onViewportChange(isInViewport)
    } else {
      if (element.textRange.startOffset in expansionRange) {
        updateCell(element)
      }
    }
  }

  private fun addInlayOutputs(inlayComponent: NotebookInlayComponentPsi,
                              inlayOutputs: List<InlayOutput>) {
    inlayComponent.addInlayOutputs(inlayOutputs) { removeInlay(inlayComponent) }
  }

  private fun removeInlay(inlayComponent: NotebookInlayComponentPsi, cleanup: Boolean = true) {
    val cell = inlayComponent.cell
    if (cleanup && cell.isValid) descriptor.cleanup(cell)
    inlayComponent.parent?.remove(inlayComponent)
    inlayComponent.disposeInlay()
    inlayComponent.dispose()
    inlays.remove(cell)
  }

  private fun addFoldingListener() {
    data class Region(val textRange: TextRange, val isExpanded: Boolean)
    val listener = object : FoldingListener {
      private val regions = ArrayList<Region>()

      override fun onFoldRegionStateChange(region: FoldRegion) {
        if(region.isValid) {
          regions.add(Region(TextRange.create(region.startOffset, region.endOffset), region.isExpanded))
        }
      }

      override fun onFoldProcessingEnd() {
        inlays.filter { pair -> isOutputPositionCollapsed(pair.key) }.forEach {
          removeInlay(it.value, cleanup = false)
        }
        inlayElements.filter { key -> regions.filter { it.isExpanded }.any { key.textRange.intersects(it.textRange) } }.forEach {
          updateCell(it)
        }
        regions.clear()
        updateInlays()
      }
    }
    editor.foldingModel.addListener(listener, editor.disposable)
  }

  private fun addDocumentListener() {
    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (project.isDisposed()) return
        if (!toolbarUpdateScheduled) {
          toolbarUpdateScheduled = true
          PsiDocumentManager.getInstance(project).performForCommittedDocument(editor.document) {
            try {
              scheduleIntervalUpdate(event.offset, event.newFragment.length)
            } finally {
              toolbarUpdateScheduled = false
            }
          }
        }
        if (!descriptor.shouldUpdateInlays(event)) return
        PsiDocumentManager.getInstance(project).performForCommittedDocument(editor.document) {
          updateInlays()
        }
      }
    }, editor.disposable)
  }

  private fun scheduleIntervalUpdate(offset: Int, length: Int) {
    val psiFile = descriptor.psiFile
    var node = psiFile.node.findLeafElementAt(offset)?.psi
    while (node != null && node.parent != psiFile) {
      node = node.parent
    }
    inlayElements.filter { !it.isValid }.forEach { getInlayComponent(it)?.let { inlay -> removeInlay(inlay) } }
    inlayElements.removeIf { !it.isValid }
    while (node != null && node.textRange.startOffset < offset + length) {
      PsiTreeUtil.collectElements(node) { psi -> descriptor.isInlayElement(psi) }.forEach { psi ->
        inlayElements.add(psi)
      }
      node = node.nextSibling
    }
  }

  /** On editor resize all inlays got width of editor. */
  private fun addResizeListener() {
    editor.component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        updateInlayComponentsWidth()
      }
    })
  }

  private fun addViewportListener() {
    editor.scrollPane.viewport.addChangeListener {
      viewportQueue.queue(object : Update(VIEWPORT_TASK_IDENTITY) {
        override fun run() {
          updateInlaysForViewport()
        }
      })
    }
  }

  private fun restoreOutputs() {
    updateInlaysForViewport()
  }

  private fun restoreToolbars(): CancellablePromise<*> {
    val inlaysPsiElements = ArrayList<PsiElement>()
    return ReadAction.nonBlocking {
      PsiTreeUtil.processElements(descriptor.psiFile) { element ->
        if (descriptor.isInlayElement(element)) inlaysPsiElements.add(element)
        true
      }
    }.finishOnUiThread(ModalityState.NON_MODAL) {
      inlayElements.clear()
      inlayElements.addAll(inlaysPsiElements)
    }.inSmartMode(project).submit(NonUrgentExecutor.getInstance())
  }

  private fun getInlayComponentByOffset(offset: Int): NotebookInlayComponentPsi? {
    return if (offset == editor.document.textLength)
      inlays.entries.firstOrNull { it.key.textRange.containsOffset(offset) }?.value
    else
      inlays.entries.firstOrNull { it.key.textRange.contains(offset) }?.value
  }

  /** Add caret listener for editor to draw highlighted background for psiCell under caret. */
  private fun addCaretListener() {
    editor.caretModel.addCaretListener(object : CaretListener {

      override fun caretPositionChanged(e: CaretEvent) {
        if (editor.caretModel.primaryCaret != e.caret) return
        onCaretPositionChanged()
      }
    }, editor.disposable)
  }

  private fun onCaretPositionChanged() {

    if (editor.isDisposed) {
      return
    }

    val cellUnderCaret = getInlayComponentByOffset(editor.logicalPositionToOffset(editor.caretModel.logicalPosition))
    if (cellUnderCaret == null) {
      inlays.values.forEach { it.selected = false }
    }
    else {
      if (!cellUnderCaret.selected) {
        inlays.values.forEach { it.selected = false }
        cellUnderCaret.selected = true
      }
    }
  }

  /** When we are adding or removing paragraphs, old cells can change their text ranges*/
  private fun updateInlays() {
    inlays.values.forEach { updateInlayPosition(it) }
  }

  private fun setupInlayComponent(inlayComponent: NotebookInlayComponentPsi) {

    fun updateInlaysInEditor(editor: Editor) {

      val end = editor.xyToLogicalPosition(Point(0, Int.MAX_VALUE))
      val offsetEnd = editor.logicalPositionToOffset(end)

      val inlays = editor.inlayModel.getBlockElementsInRange(0, offsetEnd)

      inlays.forEach { inlay ->
        if (inlay.renderer is InlayComponent) {
          (inlay.renderer as InlayComponent).updateComponentBounds(inlay)
        }
      }
    }
    inlayComponent.beforeHeightChanged = {
      scrollKeeper.savePosition()
    }
    inlayComponent.afterHeightChanged = {
      updateInlaysInEditor(editor)
      scrollKeeper.restorePosition(true)
    }
  }

  /** Aligns all editor inlays to fill full width of editor. */
  private fun updateInlayComponentsWidth() {
    val inlayWidth = InlayDimensions.calculateInlayWidth(editor)
    if (inlayWidth > 0) {
      inlays.values.forEach {
        it.setSize(inlayWidth, it.height)
        it.inlay?.updateSize()
      }
    }
  }

  /** It could be that user started to type below inlay. In this case we will detect new position and perform inlay repositioning. */
  private fun updateInlayPosition(inlayComponent: NotebookInlayComponentPsi) {
    // editedCell here contains old text. This event will be processed by PSI later.
    val offset = descriptor.getInlayOffset(inlayComponent.cell)
    if (inlayComponent.inlay!!.offset != offset) {
      inlayComponent.disposeInlay()
      val inlay = addBlockElement(offset, inlayComponent)
      inlayComponent.assignInlay(inlay)
    }
    inlayComponent.updateComponentBounds(inlayComponent.inlay!!)
  }

  private fun addBlockElement(offset: Int, inlayComponent: NotebookInlayComponentPsi): Inlay<NotebookInlayComponentPsi> {
    return editor.inlayModel.addBlockElement(offset, true, false, INLAY_PRIORITY, inlayComponent)
  }

  private fun addInlayComponent(cell: PsiElement): NotebookInlayComponentPsi {

    val existingInlay = inlays[cell]
    if (existingInlay != null) {
      throw Exception("Cell already added.")
    }

    InlayDimensions.init(editor)

    val offset = descriptor.getInlayOffset(cell)
    val inlayComponent = NotebookInlayComponentPsi(cell, editor)

    // On editor creation it has 0 width
    val gutterWidth = (editor.gutter as EditorGutterComponentEx).width
    var editorWideWidth = editor.component.width - inlayComponent.width - gutterWidth - InlayDimensions.rightBorder
    if (editorWideWidth <= 0) {
      editorWideWidth = InlayDimensions.width
    }

    inlayComponent.setBounds(0, editor.offsetToXY(offset).y + editor.lineHeight, editorWideWidth, InlayDimensions.smallHeight)
    editor.contentComponent.add(inlayComponent)
    val inlay = addBlockElement(offset, inlayComponent)

    inlayComponent.assignInlay(inlay)
    inlays[cell] = inlayComponent

    setupInlayComponent(inlayComponent)

    return inlayComponent
  }

  private fun getInlayComponent(cell: PsiElement): NotebookInlayComponentPsi? {
    return inlays[cell]
  }

  companion object {
    private const val VIEWPORT_TASK_NAME = "On viewport change"
    private const val VIEWPORT_TASK_IDENTITY = "On viewport change task"
    private const val VIEWPORT_TIME_SPAN = 50

    const val INLAY_PRIORITY = 0

    @TestOnly
    var isEnabledInTests: Boolean = false
  }
}

const val VIEWPORT_INLAY_RANGE = 20

fun calculateViewportRange(editor: EditorImpl): IntRange {
  val viewport = editor.scrollPane.viewport
  val yMin = viewport.viewPosition.y
  val yMax = yMin + viewport.height
  return yMin until yMax
}

fun calculateInlayExpansionRange(editor: EditorImpl, viewportRange: IntRange): IntRange {
  val startLine = editor.xyToLogicalPosition(Point(0, viewportRange.first)).line
  val endLine = editor.xyToLogicalPosition(Point(0, viewportRange.last + 1)).line
  val startOffset = editor.document.getLineStartOffset(max(startLine - VIEWPORT_INLAY_RANGE, 0))
  val endOffset = editor.document.getLineStartOffset(max(min(endLine + VIEWPORT_INLAY_RANGE, editor.document.lineCount - 1), 0))
  return startOffset..endOffset
}
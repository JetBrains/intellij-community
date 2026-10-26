// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.editor.IndentsModel
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.EmptyFoldingModel
import com.intellij.openapi.editor.impl.EmptyImmutableMarkupModel
import com.intellij.openapi.editor.impl.EmptyIndentsModel
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.textarea.EmptyInlayModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.geom.Point2D
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

/**
 * The imaginary editor of one side of a [UnifiedDiffViewer]. The daemon highlights the side document through it.
 */
@ApiStatus.Internal
class UnifiedSideEditor(
  private val viewer: UnifiedDiffViewer,
  project: Project,
  val side: Side,
) : ImaginaryEditor(project, viewer.getDocument(side)) {
  override fun notImplemented(): RuntimeException {
    isDisabled = true
    return UnsupportedOperationException("Not implemented. UnifiedDiffViewer.UnifiedSideEditor will be disabled.")
  }

  private fun warnMockImplementation(methodName: String) {
    if (reportedMockMethods.add(methodName)) {
      val message = "Method '$methodName' is not applicable. Consider using 'editor instanceOf ImaginaryEditor'"
      if (ApplicationManager.getApplication().isInternal || ApplicationManager.getApplication().isUnitTestMode) {
        LOG.error(message)
      }
      else {
        LOG.warn(message)
      }
    }
  }

  override fun getVirtualFile(): VirtualFile? = FileDocumentManager.getInstance().getFile(document)

  override fun isViewer(): Boolean = true

  override fun isOneLineMode(): Boolean = false

  override fun getColorsScheme(): EditorColorsScheme = viewer.editor.colorsScheme

  override fun getSettings(): EditorSettings = viewer.editor.settings

  override fun getComponent(): JComponent = viewer.editor.component // isShowing() checks

  override fun getContentComponent(): JComponent = viewer.editor.contentComponent // isShowing() checks

  override fun getMarkupModel(): MarkupModel = EmptyImmutableMarkupModel(document)

  override fun getIndentsModel(): IndentsModel = EmptyIndentsModel()

  override fun getInlayModel(): InlayModel = EmptyInlayModel()

  override fun getFoldingModel(): FoldingModel = EmptyFoldingModel()

  override fun hasHeaderComponent(): Boolean = false

  override fun getHeaderComponent(): JComponent? = null

  override fun setHeaderComponent(header: JComponent?) {
    warnMockImplementation("setHeaderComponent")
  }

  override fun getLineHeight(): Int {
    warnMockImplementation("getLineHeight")
    return viewer.editor.lineHeight
  }

  override fun visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition {
    warnMockImplementation("visualToLogicalPosition")
    return LogicalPosition(0, 0)
  }

  override fun xyToLogicalPosition(p: Point): LogicalPosition {
    warnMockImplementation("xyToLogicalPosition")
    return LogicalPosition(0, 0)
  }

  override fun logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition {
    warnMockImplementation("logicalToVisualPosition")
    return VisualPosition(0, 0)
  }

  override fun xyToVisualPosition(p: Point): VisualPosition {
    warnMockImplementation("xyToVisualPosition")
    return VisualPosition(0, 0)
  }

  override fun xyToVisualPosition(p: Point2D): VisualPosition {
    warnMockImplementation("xyToVisualPosition")
    return VisualPosition(0, 0)
  }

  override fun logicalPositionToXY(pos: LogicalPosition): Point {
    warnMockImplementation("logicalPositionToXY")
    return Point(0, 0)
  }

  override fun visualPositionToXY(visible: VisualPosition): Point {
    warnMockImplementation("visualPositionToXY")
    return Point(0, 0)
  }

  override fun visualPositionToPoint2D(pos: VisualPosition): Point2D {
    warnMockImplementation("visualPositionToPoint2D")
    return Point(0, 0)
  }

  override fun calculateVisibleRange(): ProperTextRange {
    if (viewer.diffChanges == null) return ProperTextRange(0, 0)

    val hostDocument = viewer.editor.document
    val hostRange = viewer.editor.calculateVisibleRange()
    val hostLine1 = hostDocument.getLineNumber(hostRange.startOffset)
    val hostLine2 = hostDocument.getLineNumber(hostRange.endOffset)

    val sideLine1 = viewer.transferLineFromOneside(side, maxOf(0, hostLine1 - 1))
    val sideLine2 = viewer.transferLineFromOneside(side, minOf(DiffUtil.getLineCount(hostDocument), hostLine2 + 1))
    if (sideLine1 < 0 || sideLine1 > sideLine2 || sideLine2 > DiffUtil.getLineCount(document)) return ProperTextRange(0, 0)

    val sideRange = DiffUtil.getLinesRange(document, sideLine1, sideLine2, false)
    return ProperTextRange.create(sideRange.startOffset, sideRange.endOffset)
  }

  companion object {
    private val LOG = logger<UnifiedSideEditor>()
    private val reportedMockMethods: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Set when a caller needs a method that this editor does not implement. The viewer then creates no editor of this class. */
    @JvmStatic
    var isDisabled: Boolean = false
      private set
  }
}

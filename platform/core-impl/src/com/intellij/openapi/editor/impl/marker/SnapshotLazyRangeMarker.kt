// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.openapi.vfs.isTooLarge
import com.intellij.util.DocumentUtil

/** A snapshot range marker created for a file before its document loads. */
internal class SnapshotLazyRangeMarker(
  fileRoot: VirtualFileMarkerRoot,
  spec: Byte,
  initialRange: Long,
  @Volatile private var initialLineColumns: LineColumns?,
) : SnapshotRangeMarkerImpl(fileRoot, spec, initialRange) {

  override fun getStartOffset(): Int {
    loadDocumentForInitialLineColumns()
    return super.getStartOffset()
  }

  override fun getEndOffset(): Int {
    loadDocumentForInitialLineColumns()
    return super.getEndOffset()
  }

  override fun getTextRange(): TextRange {
    loadDocumentForInitialLineColumns()
    return super.getTextRange()
  }

  override fun isValid(): Boolean {
    if (!super.isValid()) return false
    val file = (rootStorage as VirtualFileMarkerRoot).file
    if (FileDocumentManager.getInstance().getCachedDocument(file) != null) return true
    if (file.isDirectory || FileDocumentManagerBase.isBinaryWithoutDecompiler(file)) return false
    return !file.fileType.isBinary || !file.isTooLarge()
  }

  /** Returns the initial range, or [PMarkerResolution.UNRESOLVED] when no line and column data remains. */
  internal fun initialRange(document: DocumentImpl, tabSize: Int): Long {
    val lineColumns = initialLineColumns ?: return PMarkerResolution.UNRESOLVED
    val startOffset = DocumentUtil.calculateOffset(document, lineColumns.startLine, lineColumns.startColumn, tabSize)
    val endOffset = DocumentUtil.calculateOffset(document, lineColumns.endLine, lineColumns.endColumn, tabSize)
    return TextRangeScalarUtil.toScalarRange(startOffset, endOffset)
  }

  internal fun markInitialRangeResolved() {
    initialLineColumns = null
  }

  private fun loadDocumentForInitialLineColumns() {
    if (initialLineColumns != null) {
      getDocument()
    }
  }

  internal data class LineColumns(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
  )
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.ide.minimap.MinimapStructureProvider
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import java.util.IdentityHashMap

class MinimapModel(private val editor: Editor): Disposable {
  private val structureModel: StructureViewModel? = MinimapStructureProvider(editor.project, this).createModel(editor)
  private val pointerManager = editor.project?.let { SmartPointerManager.getInstance(it) }
  private var structureMarkers: List<MinimapStructureMarker> = emptyList()
  private var cachedLineProjection: MinimapLineProjection = MinimapLineProjection.identity(0)
  private var cachedLineProjectionDocumentStamp: Long = Long.MIN_VALUE
  private var cachedLineProjectionLineCount: Int = -1
  private var lineProjectionVersion: Long = 0
  private var cachedLineProjectionVersion: Long = Long.MIN_VALUE

  fun getStructureMarkers(): List<MinimapStructureMarker> = structureMarkers

  fun isDocumentCommitted(): Boolean {
    val project = editor.project ?: return true
    return PsiDocumentManager.getInstance(project).isCommitted(editor.document)
  }

  fun getLineProjection(): MinimapLineProjection {
    val document = editor.document
    val lineCount = document.lineCount
    val documentStamp = document.modificationStamp
    val projectionVersion = lineProjectionVersion
    if (cachedLineProjectionDocumentStamp == documentStamp &&
        cachedLineProjectionLineCount == lineCount &&
        cachedLineProjectionVersion == projectionVersion) {
      return cachedLineProjection
    }

    val projection = buildLineProjection(document, lineCount)
    cachedLineProjection = projection
    cachedLineProjectionDocumentStamp = documentStamp
    cachedLineProjectionLineCount = lineCount
    cachedLineProjectionVersion = projectionVersion
    return projection
  }

  fun invalidateLineProjection() {
    lineProjectionVersion++
  }

  private fun buildLineProjection(document: Document, lineCount: Int): MinimapLineProjection {
    if (lineCount <= 0) return MinimapLineProjection.identity(0)
    val textLength = document.textLength
    if (textLength <= 0) return MinimapLineProjection.identity(lineCount)

    val foldedRegions = ArrayList<Pair<Int, Int>>()
    val topLevelRegions = (editor.foldingModel as? FoldingModelEx)?.fetchTopLevel() ?: editor.foldingModel.allFoldRegions
    for (region in topLevelRegions) {
      if (!region.isValid || region.isExpanded) continue

      val startOffset = region.startOffset.coerceIn(0, textLength)
      val endOffsetExclusive = region.endOffset.coerceIn(startOffset + 1, textLength)
      if (endOffsetExclusive <= startOffset) continue

      val startLine = document.getLineNumber(startOffset)
      val endLine = document.getLineNumber((endOffsetExclusive - 1).coerceAtLeast(startOffset))
      if (endLine <= startLine) continue
      foldedRegions += startLine to endLine
    }

    if (foldedRegions.isEmpty()) return MinimapLineProjection.identity(lineCount)
    foldedRegions.sortBy { it.first }
    return MinimapLineProjection.create(lineCount, foldedRegions)
  }

  fun updateStructureMarkers() {
    if (!isDocumentCommitted()) return
    val root = structureModel?.root ?: return

    val previousStructureMarkers = structureMarkers
    val previousByElement = IdentityHashMap<StructureViewTreeElement, MinimapStructureMarker>(previousStructureMarkers.size)

    for (marker in previousStructureMarkers) {
      previousByElement[marker.element] = marker
    }
    val reusedStructureMarkers = IdentityHashMap<MinimapStructureMarker, Boolean>()
    val result = mutableListOf<MinimapStructureMarker>()
    val document = editor.document

    if (document.textLength == 0) {
      disposeStructureMarkers(previousStructureMarkers)
      structureMarkers = emptyList()
      return
    }

    val structureMarkerPolicy = MinimapStructureMarkerPolicy.forEditor(editor)

    try {
      MinimapStructureMarkerCollector(
        structureMarkerPolicy = structureMarkerPolicy,
        previousByElement = previousByElement,
        reusedStructureMarkers = reusedStructureMarkers,
        result = result,
        document = document,
        pointerManager = pointerManager,
      ).visit(root, includeSelf = false)
    }
    catch (_: IndexNotReadyException) {
      return
    }
    catch (e: IllegalStateException) {
      if (e.message?.contains("Index is not created for `Stubs`") == true) return
      throw e
    }

    val unused = previousStructureMarkers.filterNot { reusedStructureMarkers.containsKey(it) }
    disposeStructureMarkers(unused)
    structureMarkers = result
  }

  override fun dispose() {
    disposeStructureMarkers()
    structureMarkers = emptyList()
  }


  private fun disposeStructureMarkers(markersToDispose: List<MinimapStructureMarker> = structureMarkers) {
    val pointerManager = editor.project?.let { SmartPointerManager.getInstance(it) }
    for (marker in markersToDispose) {
      marker.rangeMarker?.dispose()
      marker.pointer?.let { pointerManager?.removePointer(it) }
    }
  }
}

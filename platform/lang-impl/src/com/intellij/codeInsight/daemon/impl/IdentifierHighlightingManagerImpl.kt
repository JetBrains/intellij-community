// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingManagerImpl.Companion.EDITOR_IDENT_RESULTS
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.psi.*
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus

/**
 * Identifier highlighting implementation, which contain the main entry point to the identifier highlighting: [getMarkupData]
 * Obtains the identifier highlighting info via [IdentifierHighlightingAccessor] and caches the information in document range markers under the [EDITOR_IDENT_RESULTS] key.
 * That allows to not recalculate all this information on the next caret move, if nothing changes, and the caret is still on the same declaration.
 * Implements the pull model: when the caret moves:
 * - the [com.intellij.codeInsight.highlighting.BackgroundHighlighter] starts the identifier highlighting activity in the background
 * - which calls [getMarkupData]
 * - which checks if the information for the current offset is already computed (i.e. exists [IdentifierHighlightingResult] where target range markers overlap with caret position)
 * - if not found, the call [IdentifierHighlightingAccessor.getMarkupData] which either
 * --- calls [IdentifierHighlightingComputer.computeRanges] directly in the local monolith case which computes all the necessary ranges by doing resolve/find usages etc., see [IdentifierHighlightingAccessorImpl]
 * --- or calls RPC to the server, see [com.intellij.platform.identifiers.highlighting.frontend.split.ThinIdentifierHighlightingAccessor]
 */
@ApiStatus.Internal
class IdentifierHighlightingManagerImpl(private val myProject: Project) : IdentifierHighlightingManager, Disposable {
  init {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorReleased(event: EditorFactoryEvent) {
        clearCache(event.editor.getDocument()) // to avoid leaks
      }
    }, this)
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(object : DocumentListener {
      override fun beforeDocumentChange(event: DocumentEvent) {
        val document = event.document
        clearCache(document)
      }
    }, this)
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
      override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
        val psiFile = event.file
        val document = psiFile?.getViewProvider()?.getDocument()
        if (document != null) {
          clearCache(document)
        }
      }
    }, this)
  }

  private fun clearCache(document: Document) {
    val markers: MutableList<RangeMarker> = ArrayList()
    (document as DocumentEx).processRangeMarkers(Processor { marker: RangeMarker? ->
      val data = marker!!.getUserData(IDENT_MARKUP)
      if (data != null) {
        markers.add(marker)
      }
      true
    })
    for (marker in markers) {
      marker.putUserData(IDENT_MARKUP, null)
      marker.dispose()
    }
    val virtualFile = FileDocumentManager.getInstance().getFile(document)?:return
    for (editor in FileEditorManager.getInstance(myProject).getAllEditors(virtualFile)) {
      if (editor is TextEditor) {
        editor.editor.putUserData(EDITOR_IDENT_RESULTS, null)
      }
    }
  }

  override suspend fun getMarkupData(editor: Editor, visibleRange: ProperTextRange): IdentifierHighlightingResult {
    val document = editor.getDocument()
    val modStamp = (document as DocumentEx).modificationSequence
    var result: IdentifierHighlightingResult? = null
    var bestMarker: RangeMarker? = null
    val offset = readAction { editor.getCaretModel().offset }
    readAction {
      // find the range marker marked by "IDENT_MARKUP" user data, the smallest overlapping with "offset"
      // to resolve ambiguities e.g. between exit point highlighting "return xxx" and the reference highlighting "xxx"
      document.processRangeMarkersOverlappingWith(offset, offset, Processor { marker: RangeMarker ->
        val data = marker.getUserData(IDENT_MARKUP)
        if (data != null &&
            containsTargetOffset(data, offset) &&
            (bestMarker == null || marker.textRange.length < bestMarker!!.textRange.length)
        ) {
          result = data
          bestMarker = marker
        }
        true
      })
    }
    if (result == null) {
      val psiFile = readAction {
        var psiFile = PsiUtilBase.getPsiFileInEditor(editor, myProject)
        if (psiFile is PsiCompiledFile) {
          psiFile = psiFile.getDecompiledPsiFile()
        }
        if (psiFile is PsiBinaryFile && BinaryFileTypeDecompilers.getInstance().forFileType(psiFile.getFileType()) == null) {
          psiFile = null
        }
        psiFile
      }
      if (psiFile == null) {
        result = EMPTY_RESULT
      }
      else {
        val hostRes = IdentifierHighlightingAccessor.getInstance(myProject).getMarkupData(psiFile, editor, visibleRange, offset)
        result = readAction {
          if (document.modificationSequence != modStamp) {
            throw ProcessCanceledException(RuntimeException("document changed during RPC call. modStamp before=$modStamp; mod stamp after=${document.modificationSequence}"))
          }
          val hostDocument = PsiDocumentManagerBase.getTopLevelDocument(document)
          val occurrenceRangeMarkers = ArrayList<RangeMarker>(hostRes.occurrences.size)
          val rangeMarkerOccurrences = hostRes.occurrences.map { o: IdentifierOccurrence ->
            val marker = createMarker(hostDocument, o.range)
            occurrenceRangeMarkers.add(marker)
            IdentifierOccurrence(marker, o.highlightInfoType)
          }
          val rangeMarkerTargets = hostRes.targets.map { r ->
            createMarker(hostDocument, r)
          }
          val rangeMarkerResult = IdentifierHighlightingResult(rangeMarkerOccurrences, rangeMarkerTargets)
          for (marker in occurrenceRangeMarkers) {
            marker.putUserData(IDENT_MARKUP, rangeMarkerResult)
          }
          for (marker in rangeMarkerTargets) {
            marker.putUserData(IDENT_MARKUP, rangeMarkerResult)
          }
          val editorResults = ConcurrencyUtil.computeIfAbsent(editor, EDITOR_IDENT_RESULTS) {
            ConcurrentCollectionFactory.createConcurrentSet()
          }
          editorResults.add(rangeMarkerResult)
          rangeMarkerResult
        }
      }
    }
    return result
  }

  private fun createMarker(
    document: Document,
    r: Segment,
  ): RangeMarker {
    val restricted = TextRangeScalarUtil.coerceRange(r.startOffset, r.endOffset, 0, document.textLength)
    return document.createRangeMarker(TextRangeScalarUtil.create(restricted))
  }

  override fun dispose() {
  }

  // true if this Result is valid when the caret is at offset
  private fun containsTargetOffset(result: IdentifierHighlightingResult, offset: Int): Boolean {
    return result.targets.any { t -> t.getStartOffset() <= offset && offset <= t.getEndOffset() }
  }

  companion object {
    // stored in RangeMarker user data to be able to quickly retrieve all other ranges to highlight
    private val IDENT_MARKUP = Key.create<IdentifierHighlightingResult>("IDENT_MARKUP")
    // stored in Editor to be able to hard-retain range markers from IdentifierHighlightingResult
    private val EDITOR_IDENT_RESULTS: Key<MutableCollection<IdentifierHighlightingResult>> = Key.create("EDITOR_IDENT_RESULTS")
  }
}
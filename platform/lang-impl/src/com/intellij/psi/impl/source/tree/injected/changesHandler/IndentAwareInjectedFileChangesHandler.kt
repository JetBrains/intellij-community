// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected.changesHandler

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.injected.editor.InjectionMeta
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.createSmartPointer
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.tail
import com.intellij.util.text.splitLineRanges
import kotlin.math.max

internal class IndentAwareInjectedFileChangesHandler(shreds: List<Shred>, editor: Editor, newDocument: Document, injectedFile: PsiFile) :
  CommonInjectedFileChangesHandler(shreds, editor, newDocument, injectedFile) {

  init {
    // Undo breaks fragment markers completely, so rebuilding them in that case
    myHostDocument.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (UndoManager.getInstance(myProject).isUndoInProgress) {
          (PsiDocumentManager.getInstance(myProject) as PsiDocumentManagerBase).addRunOnCommit(myHostDocument) { thisDoc ->
            rebuildMarkers(markersWholeRange(markers) ?: failAndReport("can't get marker range in undo", event))
          }
        }
      }
    }, this)

  }

  override fun commitToOriginal(e: DocumentEvent) {
    LOG.logMarkers("at beginning")
    val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    val hostPsiFile = psiDocumentManager.getPsiFile(myHostDocument) ?: failAndReport("no psiFile $myHostDocument", e)
    val affectedRange = TextRange.from(e.offset, max(e.newLength, e.oldLength))
    val affectedMarkers = markers.filter { affectedRange.intersects(it.fragmentMarker) }

    val guardedRanges = guardedBlocks.mapTo(HashSet()) { it.textRange }
    if (affectedMarkers.isEmpty() && guardedRanges.any { it.intersects(affectedRange) }) {
      // changed guarded blocks are on fragment document editor conscience, we just ignore them silently
      return
    }

    if (!hasInjectionsInOriginFile(affectedMarkers, hostPsiFile)) {
      // can't go on if there is already no injection, maybe someone just uninjected them?
      myInvalidated = true
      return
    }

    var workingRange: TextRange? = null
    val markersToRemove = SmartList<MarkersMapping>()
    val distributeTextToMarkers = distributeTextToMarkers(affectedMarkers, affectedRange, e.offset + e.newLength).let(
      this::promoteLinesEnds)
    LOG.debug {
      "distributeTextToMarkers:\n  ${distributeTextToMarkers.joinToString("\n  ") { (m, t) -> "${markerString(m)} <<< '${t.esclbr()}'" }}"
    }
    for ((affectedMarker, markerText) in distributeTextToMarkers.reversed()) {
      var rangeInHost = affectedMarker.hostMarker.textRange

      myHostEditor.caretModel.moveToOffset(rangeInHost.startOffset)
      val newText0 =
        CopyPastePreProcessor.EP_NAME.extensionList.fold(markerText) { newText, preProcessor ->
          preProcessor.preprocessOnPaste(myProject, hostPsiFile, myHostEditor, newText, null).also { r ->
            if (r != newText) {
              LOG.debug { "preprocessed by $preProcessor '${newText.esclbr()}' -> '${r.esclbr()}'" }
            }
          }
        }.let { preprocessed ->
          val firstLineWhiteSpaces = markerText.takeWhile { it.isWhitespace() }
          if (preprocessed.startsWith(firstLineWhiteSpaces)) preprocessed
          else firstLineWhiteSpaces + preprocessed
        }

      val indent = affectedMarker.host?.getUserData(InjectionMeta.getInjectionIndent())
      val newText = indentHeuristically(indent, newText0, newText0 != markerText)
      LOG.debug { "newTextIndentAware:'${newText.esclbr()}' markerText:'${markerText.esclbr()}'" }

      //TODO: cover additional clauses with tests (multiple languages injected into one host)
      if (newText.isEmpty() && affectedMarker.fragmentRange !in guardedRanges && affectedMarker.host?.contentRange == rangeInHost) {
        markersToRemove.add(affectedMarker)
      }

      val oldText = myHostDocument.charsSequence.subSequence(rangeInHost.startOffset, rangeInHost.endOffset)

      if (indent != null && oldText.endsWith("\n") && !newText.endsWith("\n")) {
        if (myHostDocument.charsSequence.let { it.subSequence(rangeInHost.endOffset, it.length) }.startsWith(indent)) {
          rangeInHost = TextRange(rangeInHost.startOffset, rangeInHost.endOffset + indent.length)
        }
      }

      LOG.debug { "replacing: '${oldText.toString().esclbr()}' <<< '${newText.esclbr()}'" }
      myHostDocument.replaceString(rangeInHost.startOffset, rangeInHost.endOffset, newText)
      workingRange = workingRange union TextRange.from(rangeInHost.startOffset, newText.length)
    }

    workingRange = workingRange ?: failAndReport("no workingRange", e)
    LOG.logMarkers("before commit")
    psiDocumentManager.commitDocument(myHostDocument)

    if (distributeTextToMarkers.none { (marker, text) -> marker.isValid() && text.isNotEmpty() }) {
      affectedMarkers.asSequence().mapNotNull { it.host }.firstOrNull()?.let { host ->
        val indent = host.getUserData(InjectionMeta.getInjectionIndent())
        val indented = indentHeuristically(indent, myFragmentDocument.text, false)
        workingRange = workingRange union ElementManipulators.handleContentChange(host, indented)?.textRange
      }
    }

    LOG.logMarkers("after reformat")
    rebuildMarkers(workingRange!!)
    LOG.logMarkers("after rebuild")
    updateFileContextElementIfNeeded(hostPsiFile, workingRange!!)
  }

  private fun indentHeuristically(indent: String?, newText0: String, maybeIndented: Boolean): String {
    val lines = splitLineRanges(newText0).map { it.subSequence(newText0) }.toList()
    return when {
      indent == null -> newText0
      // on the following line we heuristically guess that it was already indented by KotlinLiteralCopyPasteProcessor
      // TODO: come to the agreement with KotlinLiteralCopyPasteProcessor who will eventually indent eveything
      maybeIndented && lines.all { it == "\n" || it.startsWith(indent) } -> newText0
      lines.size <= 1 -> newText0
      else -> buildString {
        append(lines.first())
        for (remaining in lines.tail()) {
          append(indent)
          append(remaining)
        }
      }
    }
  }

  private fun updateFileContextElementIfNeeded(hostPsiFile: PsiFile, workingRange: TextRange) {
    val fragmentPsiFile = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(myFragmentDocument) ?: return

    val injectedPointer = fragmentPsiFile.getUserData(FileContextUtil.INJECTED_IN_ELEMENT) ?: return
    if (injectedPointer.element != null) return // still valid no need to update

    val newHost = getInjectionHostAtRange(hostPsiFile, workingRange) ?: return
    fragmentPsiFile.putUserData(FileContextUtil.INJECTED_IN_ELEMENT, newHost.createSmartPointer())
  }

  private var myInvalidated = false

  override fun isValid(): Boolean = !myInvalidated && super.isValid()

  private fun hasInjectionsInOriginFile(affectedMarkers: List<MarkersMapping>, hostPsiFile: PsiFile): Boolean {
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)
    return affectedMarkers.any { marker ->
      marker.hostElementRange?.let { injectedLanguageManager.getCachedInjectedDocumentsInRange(hostPsiFile, it).isNotEmpty() }
      ?: false
    }
  }
  
  private val guardedBlocks get() = (myFragmentDocument as DocumentEx).guardedBlocks

  private fun promoteLinesEnds(mapping: List<Pair<MarkersMapping, String>>): Iterable<Pair<MarkersMapping, String>> {
    val result = ArrayList<Pair<MarkersMapping, String>>(mapping.size)
    var transfer = ""
    val reversed = ContainerUtil.reverse(mapping)
    for (i in reversed.indices) {
      val (marker, text) = reversed[i]
      if (text == "\n" && reversed.getOrNull(i + 1)?.second?.endsWith("\n") == false) {
        result.add(Pair(marker, ""))
        transfer += "\n"
      }
      else if (transfer != "") {
        result.add(Pair(marker, text + transfer))
        transfer = ""
      }
      else {
        result.add(reversed[i])
      }
    }
    return ContainerUtil.reverse(result)
  }

  private fun markersWholeRange(affectedMarkers: List<MarkersMapping>): TextRange? =
    affectedMarkers.asSequence()
      .filter { it.host?.isValid ?: false }
      .mapNotNull { it.hostElementRange }
      .takeIf { it.any() }?.reduce(TextRange::union)

}

private val LOG: Logger = logger<IndentAwareInjectedFileChangesHandler>()

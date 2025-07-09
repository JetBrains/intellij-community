// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.*
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.impl.source.tree.injected.changesHandler.*
import com.intellij.psi.util.createSmartPointer
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import kotlin.math.max

internal class JavaInjectedFileChangesHandler(shreds: List<Shred>, editor: Editor, newDocument: Document, injectedFile: PsiFile) :
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
    for ((affectedMarker, markerText) in distributeTextToMarkers(affectedMarkers, affectedRange, e.offset + e.newLength)
      .let(this::promoteLinesEnds)) {
      val rangeInHost = affectedMarker.hostMarker.textRange

      myHostEditor.caretModel.moveToOffset(rangeInHost.startOffset)
      val newText = CopyPastePreProcessor.EP_NAME.extensionList.fold(markerText) { newText, preProcessor ->
        preProcessor.preprocessOnPaste(myProject, hostPsiFile, myHostEditor, newText, null)
      }

      //TODO: cover additional clauses with tests (multiple languages injected into one host)
      if (newText.isEmpty() && affectedMarker.fragmentRange !in guardedRanges && affectedMarker.host?.contentRange == rangeInHost) {
        markersToRemove.add(affectedMarker)
      }

      myHostDocument.replaceString(rangeInHost.startOffset, rangeInHost.endOffset, newText)
      workingRange = workingRange union TextRange.from(rangeInHost.startOffset, newText.length)
    }

    workingRange = workingRange ?: failAndReport("no workingRange", e)
    psiDocumentManager.commitDocument(myHostDocument)
    if (markersToRemove.isNotEmpty()) {
      val remainingRange = removeHostsFromConcatenation(markersToRemove)
      if (remainingRange != null) {
        workingRange = remainingRange
        getInjectionHostAtRange(hostPsiFile, remainingRange)?.let { host ->
          myHostEditor.caretModel.moveToOffset(
            ElementManipulators.getOffsetInElement(host) + host.textRange.startOffset
          )
        }
      }

    }

    val wholeRange = (markersWholeRange(affectedMarkers) union workingRange) ?: failAndReport("no wholeRange", e)

    CodeStyleManager.getInstance(myProject).reformatRange(
      hostPsiFile, wholeRange.startOffset, wholeRange.endOffset, true)

    rebuildMarkers(workingRange)

    updateFileContextElementIfNeeded(hostPsiFile, workingRange)
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
      marker.hostElementRange?.let { injectedLanguageManager.getCachedInjectedDocumentsInRange(hostPsiFile, it).isNotEmpty() } ?: false
    }
  }

  override fun rebuildMarkers(contextRange: TextRange) {
    val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    psiDocumentManager.commitDocument(myHostDocument)

    val hostPsiFile = psiDocumentManager.getPsiFile(myHostDocument) ?: failAndReport("no psiFile $myHostDocument")
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)

    val newInjectedFile = getInjectionHostAtRange(hostPsiFile, contextRange)?.let { host ->

      val injectionRange = run {
        val hostRange = host.textRange
        val contextRangeTrimmed = hostRange.intersection(contextRange) ?: hostRange
        contextRangeTrimmed.shiftLeft(hostRange.startOffset)
      }

      injectedLanguageManager.getInjectedPsiFiles(host)
        .orEmpty()
        .asSequence()
        .filter { (_, range) -> range.length > 0 && injectionRange.contains(range) }
        .mapNotNull { it.first as? PsiFile }
        .firstOrNull()
    }

    if (newInjectedFile !== myInjectedFile) {
      myInjectedFile = newInjectedFile ?: myInjectedFile

      markers.forEach { it.dispose() }
      markers.clear()

      //some hostless shreds could exist for keeping guarded values
      val hostfulShreds = InjectedLanguageUtil.getShreds(myInjectedFile).filter { it.host != null }
      val markersFromShreds = getMarkersFromShreds(hostfulShreds)
      markers.addAll(markersFromShreds)
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

  private fun getFollowingElements(host: PsiLanguageInjectionHost): List<PsiElement>? {
    val result = SmartList<PsiElement>()
    for (sibling in host.nextSiblings) {
      if (intermediateElement(sibling)) {
        result.add(sibling)
      }
      else {
        return result
      }
    }
    return null
  }

  private fun removeHostsFromConcatenation(hostsToRemove: List<MarkersMapping>): TextRange? {
    val psiPolyadicExpression = hostsToRemove.asSequence().mapNotNull { it.host?.parent as? PsiPolyadicExpression }.distinct().singleOrNull()

    for (marker in hostsToRemove.reversed()) {
      val host = marker.host ?: continue
      val relatedElements = getFollowingElements(host) ?: host.prevSiblings.takeWhile(::intermediateElement).toList()
      if (relatedElements.isNotEmpty()) {
        host.delete()
        marker.hostMarker.dispose()
        for (related in relatedElements) {
          if (related.isValid) {
            related.delete()
          }
        }
      }
    }

    if (psiPolyadicExpression != null) {
      // distinct because Java could duplicate operands sometimes (EA-142380), mb something is wrong with the removal code upper ?
      psiPolyadicExpression.operands.distinct().singleOrNull()?.let { onlyRemaining ->
        return psiPolyadicExpression.replace(onlyRemaining).textRange
      }
    }

    return null
  }

}

private fun intermediateElement(psi: PsiElement) =
  psi is PsiWhiteSpace || (psi is PsiJavaToken && psi.tokenType == JavaTokenType.PLUS)

private val PsiElement.nextSiblings: Sequence<PsiElement>
  get() = generateSequence(this.nextSibling) { it.nextSibling }

private val PsiElement.prevSiblings: Sequence<PsiElement>
  get() = generateSequence(this.prevSibling) { it.prevSibling }


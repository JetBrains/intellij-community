// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.Trinity
import com.intellij.psi.*
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.source.tree.injected.changesHandler.*
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import kotlin.math.max

internal class JavaInjectedFileChangesHandler(shreds: List<Shred>, editor: Editor, newDocument: Document, injectedFile: PsiFile) :
  CommonInjectedFileChangesHandler(shreds, editor, newDocument, injectedFile) {

  init {
    // Undo breaks local markers completely, so rebuilding them in that case
    myOrigDocument.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (UndoManager.getInstance(myProject).isUndoInProgress) {
          PsiDocumentManagerBase.addRunOnCommit(myOrigDocument) {
            rebuildMarkers(markersWholeRange(markers) ?: failAndReport("cant get marker range"))
          }
        }
      }
    }, this)

  }

  override fun commitToOriginal(e: DocumentEvent) {
    val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    val origPsiFile = psiDocumentManager.getPsiFile(myOrigDocument) ?: failAndReport("no psiFile $myOrigDocument", e)
    val affectedRange = TextRange.from(e.offset, max(e.newLength, e.oldLength))
    val affectedMarkers = markers.filter { affectedRange.intersects(it.local) }

    val guardedRanges = guardedBlocks.mapTo(HashSet()) { it.range }
    if (affectedMarkers.isEmpty() && guardedRanges.any { it.intersects(affectedRange) }) {
      // changed guarded blocks are on fragment document editor conscience, we just ignore them silently
      return
    }

    var workingRange: TextRange? = null
    val markersToRemove = SmartList<Marker>()
    for ((affectedMarker, markerText) in distributeTextToMarkers(affectedMarkers, affectedRange, e.offset + e.newLength)
      .let(this::promoteLinesEnds)) {
      val rangeInHost = affectedMarker.origin.range

      myEditor.caretModel.moveToOffset(rangeInHost.startOffset)
      val newText = CopyPastePreProcessor.EP_NAME.extensionList.fold(markerText) { newText, preProcessor ->
        preProcessor.preprocessOnPaste(myProject, origPsiFile, myEditor, newText, null)
      }

      //TODO: cover additional clauses with tests (multiple languages injected into one host)
      if (newText.isEmpty() && affectedMarker.localRange !in guardedRanges && affectedMarker.host?.contentRange == rangeInHost) {
        markersToRemove.add(affectedMarker)
      }

      myOrigDocument.replaceString(rangeInHost.startOffset, rangeInHost.endOffset, newText)
      workingRange = workingRange union TextRange.from(rangeInHost.startOffset, newText.length)
    }

    workingRange = markersWholeRange(affectedMarkers) union workingRange ?: failAndReport("no workingRange", e)
    psiDocumentManager.commitDocument(myOrigDocument)
    if (markersToRemove.isNotEmpty()) {
      val remainingRange = removeHostsFromConcatenation(markersToRemove)
      if (remainingRange != null) {
        workingRange = remainingRange
        getInjectionHostsAtRange(origPsiFile, remainingRange).firstOrNull()?.let { host ->
          myEditor.caretModel.moveToOffset(
            ElementManipulators.getManipulator(host).getRangeInElement(host).startOffset + host.textRange.startOffset
          )
        }
      }

    }
    CodeStyleManager.getInstance(myProject).reformatRange(
      origPsiFile, workingRange.startOffset, workingRange.endOffset, true)

    rebuildMarkers(workingRange)
  }

  private fun rebuildMarkers(contextRange: TextRange) {
    val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    psiDocumentManager.commitDocument(myOrigDocument)

    val origPsiFile = psiDocumentManager.getPsiFile(myOrigDocument) ?: failAndReport("no psiFile $myOrigDocument")
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)

    // kind of heuristics, strange things will happen if there are multiple injected files in one host
    val injectedPsiFiles = getInjectionHostsAtRange(origPsiFile, contextRange).firstOrNull()?.let { host ->
      injectedLanguageManager.getInjectedPsiFiles(host)?.mapNotNull { it.first as? PsiFile }
    }.orEmpty()

    val newInjectedFile = injectedPsiFiles.firstOrNull()
    if (newInjectedFile != null && newInjectedFile !== myInjectedFile) {
      myInjectedFile = newInjectedFile
    }

    markers.forEach { it.origin.dispose(); it.local.dispose() }
    markers.clear()

    //some hostless shreds could exist for keeping guarded values
    val hostfulShreds = InjectedLanguageUtil.getShreds(myInjectedFile).filter { it.host != null }
    val markersFromShreds = getMarkersFromShreds(hostfulShreds)
    markers.addAll(markersFromShreds)
  }

  private fun getInjectionHostsAtRange(origPsiFile: PsiFile, contextRange: TextRange): Sequence<PsiLanguageInjectionHost> =
    origPsiFile.findElementAt(contextRange.startOffset)?.withNextSiblings.orEmpty()
      .takeWhile { it.textRange.startOffset < contextRange.endOffset }
      .flatMap { sequenceOf(it, it.parent) }
      .filterIsInstance<PsiLanguageInjectionHost>()


  private val guardedBlocks get() = (myNewDocument as DocumentEx).guardedBlocks

  private fun promoteLinesEnds(mapping: List<Pair<Marker, String>>): Iterable<Pair<Marker, String>> {
    val result = ArrayList<Pair<Marker, String>>(mapping.size);
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

  private fun markersWholeRange(affectedMarkers: List<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer<PsiLanguageInjectionHost>>>): TextRange? =
    affectedMarkers.asSequence()
      .filter { it.host?.isValid ?: false }
      .mapNotNull { it.hostSegment?.range }
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

  private fun removeHostsFromConcatenation(hostsToRemove: List<Marker>): TextRange? {
    for (marker in hostsToRemove.reversed()) {
      val host = marker.host ?: continue
      val relatedElements = getFollowingElements(host) ?: host.prevSiblings.takeWhile(::intermediateElement).toList()
      if (relatedElements.isNotEmpty()) {
        host.delete()
        marker.origin.dispose()
        for (related in relatedElements) {
          if (related.isValid) {
            related.delete()
          }
        }
      }
    }

    val psiPolyadicExpression = hostsToRemove.asSequence().mapNotNull { it.host?.parent as? PsiPolyadicExpression }.distinct().singleOrNull()
    if (psiPolyadicExpression != null) {
      psiPolyadicExpression.operands.singleOrNull()?.let { onlyRemaining ->
        return psiPolyadicExpression.replace(onlyRemaining).textRange
      }
    }

    return null
  }

}

private infix fun TextRange?.union(another: TextRange?) = another?.let { this?.union(it) ?: it } ?: this

private fun intermediateElement(psi: PsiElement) =
  psi is PsiWhiteSpace || (psi is PsiJavaToken && psi.tokenType == JavaTokenType.PLUS)

private val PsiElement.nextSiblings: Sequence<PsiElement>
  get() = generateSequence(this.nextSibling) { it.nextSibling }

private val PsiElement.withNextSiblings: Sequence<PsiElement>
  get() = generateSequence(this) { it.nextSibling }

private val PsiElement.prevSiblings: Sequence<PsiElement>
  get() = generateSequence(this.prevSibling) { it.prevSibling }


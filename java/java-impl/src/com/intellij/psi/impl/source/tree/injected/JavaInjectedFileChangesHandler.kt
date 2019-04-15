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


  private val myOrigCreationStamp = myOrigDocument.modificationStamp // store creation stamp for UNDO tracking

  init {
    // Undo breaks local markers completely, so rebuilding them in that case
    myOrigDocument.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (UndoManager.getInstance(myProject).isUndoInProgress) {
          PsiDocumentManagerBase.addRunOnCommit(myOrigDocument) {
            if (myOrigCreationStamp <= myOrigDocument.modificationStamp) {
              rebuildMarkers(markersWholeRange(markers) ?: failAndReport("cant get marker range"))
            }
          }
        }
      }
    }, this)

  }

  override fun commitToOriginal(e: DocumentEvent) {
    //    myInjectedFile.let { if (!it.isValid) throw PsiInvalidElementAccessException(it) }
    val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    val origPsiFile = psiDocumentManager.getPsiFile(myOrigDocument)!!
    val affectedRange = TextRange.from(e.offset, max(e.newLength, e.oldLength))
    println("e = $e")
    println("affectedRange = $affectedRange")
    val affectedMarkers = markers.filter { affectedRange.intersects(it.local) }
    println("affectedMarkers = ${affectedMarkers.size}: ${affectedMarkers.map { it.origin.range }}")

    var workingRange: TextRange? = null
    val markersToRemove = SmartList<Marker>()
    for ((affectedMarker, markerText) in mapMarkersToText(affectedMarkers, affectedRange, e.offset + e.newLength)
      .let(this::promoteLinesEnds)) {

      val rangeInHost = affectedMarker.origin.range
      println("range = ${logHostMarker(rangeInHost)}")
      println("markerText = '$markerText'")

      val newText = CopyPastePreProcessor.EP_NAME.extensionList.fold(markerText) { newText, preProcessor ->
        preProcessor.preprocessOnPaste(myProject, origPsiFile, myEditor, newText, null)
      }

      println("newText = '$newText'")
      println("replacing ${logHostMarker(rangeInHost)} with '$newText'")
      if (newText.isEmpty()) {
        markersToRemove.add(affectedMarker)
      }

      myOrigDocument.replaceString(rangeInHost.startOffset, rangeInHost.endOffset, newText)
      val changedRange = TextRange.from(rangeInHost.startOffset, newText.length)
      println("workingRange accumulated = ${logHostMarker(workingRange)}")
      workingRange = workingRange union changedRange
    }

    workingRange = markersWholeRange(affectedMarkers) union workingRange ?: failAndReport("no workingRange", e)

    psiDocumentManager.commitDocument(myOrigDocument)
    println("workingRange = ${logHostMarker(workingRange)}")

    if (markersToRemove.isNotEmpty()) {
      workingRange = removeHostsFromConcatenation(markersToRemove) ?: workingRange
    }

    println("range to reformat = ${logHostMarker(workingRange)}")

    CodeStyleManager.getInstance(myProject).reformatRange(
      origPsiFile, workingRange.startOffset, workingRange.endOffset, true)

    println("workingRange-after = ${logHostMarker(workingRange)}")
    rebuildMarkers(workingRange)
  }

  private fun rebuildMarkers(contextRange: TextRange) {
    val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    psiDocumentManager.commitDocument(myOrigDocument)

    val origPsiFile = psiDocumentManager.getPsiFile(myOrigDocument)!!
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)

    println("searching for injections at ${logHostMarker(contextRange)}")
    val injectedPsiFiles = run {
      /*injectedLanguageManager.getCachedInjectedDocumentsInRange(origPsiFile, contextRange)
        .mapNotNull(psiDocumentManager::getPsiFile)
        .takeIf { it.isNotEmpty() }
      ?:*/

      //      injectedLanguageManager.findInjectedElementAt(origPsiFile, contextRange.startOffset + 1)?.let {
      //        listOf(it.containingFile)
      //      }
      getInjectionHostsAtRange(origPsiFile, contextRange).firstOrNull().also {
        println("findElementOfClassAtRange = $it")
      }?.let { host ->
        injectedLanguageManager.getInjectedPsiFiles(host)?.mapNotNull { it.first as? PsiFile }
      }
      ?: emptyList()
    }

    println("injectedPsiFiles = $injectedPsiFiles")

    val newInjectedFile = injectedPsiFiles.firstOrNull()
    if (newInjectedFile != null && newInjectedFile !== myInjectedFile) {
      println("injected file updated")
      myInjectedFile = newInjectedFile
    }
    markers.forEach { it.origin.dispose(); it.local.dispose() }
    markers.clear()
    println("injectedFile: '${myInjectedFile.text}'")
    println("rebuilding makers myNewDocument= '${myNewDocument.text}'")
    val shreds = InjectedLanguageUtil.getShreds(myInjectedFile)
    println("shreds = \n   ${shreds.joinToString("\n   ") { myNewDocument.logMarker(it.innerRange) }}")
    val markersFromShreds = getMarkersFromShreds(shreds)
    println("markersFromShreds = \n   ${markersFromShreds.joinToString("\n\n   ") { marker ->
      "oring=${myOrigDocument.logMarker(marker.origin.range)}\n   " +
      "local=${myNewDocument.logMarker(marker.local.range)}"
    }}")
    markers.addAll(markersFromShreds)
  }

  private fun getInjectionHostsAtRange(origPsiFile: PsiFile, contextRange: TextRange): Sequence<PsiLanguageInjectionHost> =
    origPsiFile.findElementAt(contextRange.startOffset)?.withNextSiblings.orEmpty()
      .takeWhile { it.textRange.startOffset < contextRange.endOffset }
      .flatMap { sequenceOf(it, it.parent) }
      .filterIsInstance<PsiLanguageInjectionHost>()

  private fun logHostMarker(rangeInHost: TextRange?) = myOrigDocument.logMarker(rangeInHost)

  private fun Document.logMarker(rangeInHost: TextRange?): String = "$rangeInHost -> '${rangeInHost?.let {
    try {
      getText(it)
    }
    catch (e: IndexOutOfBoundsException) {
      e.toString()
    }
  }}'"

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

  private fun markersWholeRange(affectedMarkers: List<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer<PsiLanguageInjectionHost>>>): TextRange? {
    return affectedMarkers.asSequence()
      .filter { it.host?.isValid ?: false }
      .mapNotNull { it.hostSegment?.range }
      .takeIf { it.any() }?.reduce(TextRange::union)
  }

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
    val psiPolyadicExpression = hostsToRemove.asSequence().mapNotNull { it.host?.parent as? PsiPolyadicExpression }.distinct().singleOrNull()
    println("hostsToRemove = $hostsToRemove")
    for (marker in hostsToRemove.reversed()) {
      val host = marker.host
      if (host == null) {
        println("no host at ${logHostMarker(marker.hostSegment?.range)}")
        continue
      }

      val relatedElements =
        getFollowingElements(host) ?: host.prevSiblings.takeWhile(::intermediateElement).toList()

      println("removing ${relatedElements.map { it to it.textRange }}")
      if (relatedElements.isNotEmpty()) {
        host.delete()
        marker.origin.dispose()
        for (related in relatedElements) {
          if (related.isValid) {
            related.delete()
          }
          else {
            println("invalid psi element = $related")
          }
        }
      }
    }

    if (psiPolyadicExpression != null) {
      psiPolyadicExpression.operands.singleOrNull()?.let { onlyRemaining ->
        println("replacing last $onlyRemaining")
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


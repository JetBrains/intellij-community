// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.Trinity
import com.intellij.psi.*
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.injected.changesHandler.*
import com.intellij.util.SmartList
import kotlin.math.max

internal class JavaInjectedFileChangesHandler(shreds: List<Shred>, editor: Editor, newDocument: Document, injectedFile: PsiFile) :
  CommonInjectedFileChangesHandler(shreds, editor, newDocument, injectedFile) {

  override fun commitToOriginal(e: DocumentEvent) {
    val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
    val origPsiFile = psiDocumentManager.getPsiFile(myOrigDocument)!!
    val affectedRange = TextRange.from(e.offset, max(e.newLength, e.oldLength))
    println("e = $e")
    println("affectedRange = $affectedRange")
    val affectedMarkers = markers.filter { affectedRange.intersects(it.local) }
    println("affectedMarkers = ${affectedMarkers.size}: ${affectedMarkers.map { it.origin.range }}")

    var rangeToReformat: TextRange? = null

    var accumulatedShift = 0

    val hostsToRemove = SmartList<Marker>()
    val survivedMarkers = SmartList<Marker>()

    for ((affectedMarker, markerText) in mapMarkersToText(affectedMarkers, affectedRange, e.offset + e.newLength)) {
      assert(myInjectedFile.isValid, { "injected file should be valid" })
      val rangeInHost = affectedMarker.origin.range
      println("range = $rangeInHost -> '${myOrigDocument.getText(rangeInHost)}'")
      println("markerText = '$markerText'")
      val host = affectedMarker.host
      if (host == null) {
        println("no host for text:'$markerText' affectedMarker = ${affectedMarker.third.range}")
        continue
      }

      println("accumulatedShift = $accumulatedShift")
      //      val contentTextRange = host.contentRange.shiftRight(accumulatedShift)

      myEditor.caretModel.moveToOffset(rangeInHost.startOffset)

      val newText = CopyPastePreProcessor.EP_NAME.extensionList.fold(markerText) { newText, preProcessor ->
        preProcessor.preprocessOnPaste(myProject, origPsiFile, myEditor, newText, null).also {
          //          println("newText = '$it' by $preProcessor")
        }
      }

      println("newText = '$newText'")

      println("replacing '${myOrigDocument.getText(rangeInHost)}' with '$newText'")
      if (newText.isEmpty()) {
        hostsToRemove.add(affectedMarker)
      }
      else {
        survivedMarkers.add(affectedMarker)
      }

      accumulatedShift += newText.length - rangeInHost.length
      myOrigDocument.replaceString(rangeInHost.startOffset, rangeInHost.endOffset, newText)
      val changedRange = TextRange.from(rangeInHost.startOffset, newText.length)
      rangeToReformat = rangeToReformat?.union(changedRange) ?: changedRange
    }

    rangeToReformat = markersWholeRange(survivedMarkers) ?: rangeToReformat

    psiDocumentManager.commitDocument(myOrigDocument)
    println("rangeToReformat = $rangeToReformat -> '${rangeToReformat?.substring(origPsiFile.text)}'")
    if (rangeToReformat != null) {

      CodeStyleManager.getInstance(myProject).reformatRange(
        origPsiFile, rangeToReformat.startOffset, rangeToReformat.endOffset, true)



      if (hostsToRemove.isNotEmpty()) {
        rangeToReformat = removeHostsFromConcatenation(hostsToRemove) ?: rangeToReformat
        println("hostsToRemove remainig = $hostsToRemove")
        if (hostsToRemove.isNotEmpty()) {
          //          rangeToReformat = hostsToRemove.map { it.textRange }.reduce(TextRange::union)
          rangeToReformat = markersWholeRange(survivedMarkers) ?: rangeToReformat
          CodeStyleManager.getInstance(myProject).reformatRange(
            origPsiFile, rangeToReformat.startOffset, rangeToReformat.endOffset, true)
        }

      }


    }
    rangeToReformat = markersWholeRange(survivedMarkers) ?: rangeToReformat
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)
    //    InjectedLanguageUtil.getShreds(myInjectedFile).let { shreds ->
    //      println("shreds count = ${shreds.size}")
    //    }

    if (rangeToReformat != null) {


      val injectedPsiFiles = run {
        //        hostsToRemove.asSequence().mapNotNull { it.host }.firstOrNull()?.let { host ->
        //          myEditor.caretModel.moveToOffset(host.contentRange.startOffset)
        //          return@run injectedLanguageManager.getInjectedPsiFiles(host)?.mapNotNull { it.first as? PsiFile }.orEmpty()
        //        }

        println("searching for injections at $rangeToReformat -> '${rangeToReformat?.substring(origPsiFile.text.also {
          println("origPsiFile.text = '${origPsiFile.text}")
        })}'")

        val injectedDocumentsInRange = injectedLanguageManager.getCachedInjectedDocumentsInRange(origPsiFile, rangeToReformat)
        injectedDocumentsInRange.map { dw -> psiDocumentManager.getPsiFile(dw) }
      }



      //    affectedMarkers.asSequence().mapNotNull { it.host }.firstOrNull()?.let {host ->
      //      val injectedPsiFiles = injectedLanguageManager.getInjectedPsiFiles(host)?.map { it.first }.orEmpty()
      println("injectedPsiFiles = $injectedPsiFiles")
      for (psiFile in injectedPsiFiles.filterIsInstance<PsiFile>()) {
        val shreds = InjectedLanguageUtil.getShreds(psiFile)
        println("shreds1 count = ${shreds.size} for $psiFile")
      }
      //    }

      val newInjectedFile = injectedPsiFiles.first()
      if (newInjectedFile != null && newInjectedFile !== myInjectedFile) {
        println("injected file updated")
        myInjectedFile = newInjectedFile
        markers.clear()
        val markersFromShreds = getMarkersFromShreds(InjectedLanguageUtil.getShreds(myInjectedFile))
        println("markersFromShreds = $markersFromShreds")
        markers.addAll(markersFromShreds)
        markers.firstOrNull()?.host?.contentRange?.let {
          myEditor.caretModel.moveToOffset(it.startOffset)
        }
      }
    }

  }

  private fun markersWholeRange(affectedMarkers: List<Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer<PsiLanguageInjectionHost>>>): TextRange? {
    return affectedMarkers.asSequence()
      .filter { it.host?.isValid ?: false }
      .mapNotNull { it.hostRange?.let { TextRange.create(it) } }
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

  private fun removeHostsFromConcatenation(hostsToRemove: MutableList<Marker>): TextRange? {
    val psiPolyadicExpression = hostsToRemove.asSequence().mapNotNull { it.host?.parent as? PsiPolyadicExpression }.distinct().single()
    println("hostsToRemove = $hostsToRemove")
    val allToDelete = SmartList<PsiElement>()
    for (marker in hostsToRemove.reversed()) {

      val host = marker.host!!

      val relatedElements =
        getFollowingElements(host) ?: host.prevSiblings.takeWhile(::intermediateElement).toList()

      println("removing ${relatedElements.map { it to it.textRange }}")

      if (relatedElements.isNotEmpty()) {
        host.delete()
        marker.origin.dispose()
        hostsToRemove.remove(marker)
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

    psiPolyadicExpression.operands.singleOrNull()?.let { onlyRemaining ->
      println("replacing last $onlyRemaining")
      return psiPolyadicExpression.replace(onlyRemaining).textRange
      //            .let { new ->
      //            hostsToRemove.remove(onlyRemaining as PsiLanguageInjectionHost)
      //            hostsToRemove.add(new as PsiLanguageInjectionHost)
      //          }
    }

    return null

  }



  private val PsiLanguageInjectionHost.contentRange
    get() = ElementManipulators.getManipulator(this).getRangeInElement(this).shiftRight(textRange.startOffset)

  override fun tryReuse(newInjectedFile: PsiFile, newHostRange: TextRange): Boolean {
    println("tryReuse = $newInjectedFile")
    return super.tryReuse(newInjectedFile, newHostRange)
  }
}

private fun intermediateElement(psi: PsiElement) =
  psi is PsiWhiteSpace || (psi is PsiJavaToken && psi.tokenType == JavaTokenType.PLUS)

private val PsiElement.nextSiblings: Sequence<PsiElement>
  get() = generateSequence(this.nextSibling) { it.nextSibling }

private val PsiElement.prevSiblings: Sequence<PsiElement>
  get() = generateSequence(this.prevSibling) { it.prevSibling }


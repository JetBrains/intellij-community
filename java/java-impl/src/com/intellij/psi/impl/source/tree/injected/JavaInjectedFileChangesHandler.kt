// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.injected.changesHandler.*
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
    println("affectedMarkers = ${affectedMarkers.size}")

    var rangeToReformat: TextRange? = null

    for ((affectedMarker, markerText) in mapMarkersToText(affectedMarkers, affectedRange, e.offset + e.newLength)) {
      val range = affectedMarker.origin.range
      println("range = $range -> '${affectedMarker.origin.document.getText(range)}'")
      println("markerText = '$markerText'")
      val host = affectedMarker.host
      if (host == null) {
        println("no host for text:'$markerText' affectedMarker = ${affectedMarker.third.range}")
        continue
      }

      val contentTextRange = host.contentRange

      myEditor.caretModel.moveToOffset(contentTextRange.startOffset)

      val newText = CopyPastePreProcessor.EP_NAME.extensionList.fold(markerText) { newText, preProcessor ->
        preProcessor.preprocessOnPaste(myProject, origPsiFile, myEditor, newText, null).also {
          //          println("newText = '$it' by $preProcessor")
        }
      }

      println("newText = '$newText'")

      println("replacing '${myOrigDocument.getText(contentTextRange)}' with '$newText'")

      myOrigDocument.replaceString(contentTextRange.startOffset, contentTextRange.endOffset, newText)
      val changedRange = TextRange.from(contentTextRange.startOffset, newText.length)
      rangeToReformat = rangeToReformat?.union(changedRange) ?: changedRange
    }
    psiDocumentManager.commitDocument(myOrigDocument)
    println("rangeToReformat = $rangeToReformat")
    if (rangeToReformat != null) {

      CodeStyleManager.getInstance(myProject).reformatRange(
        origPsiFile, rangeToReformat.startOffset, rangeToReformat.endOffset, true)
    }
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)
    InjectedLanguageUtil.getShreds(myInjectedFile).let { shreds ->
      println("shreds count = ${shreds.size}")
    }

    if (rangeToReformat != null) {
      val injectedDocumentsInRange = injectedLanguageManager.getCachedInjectedDocumentsInRange(origPsiFile, rangeToReformat)

      val injectedPsiFiles = injectedDocumentsInRange.map { dw -> psiDocumentManager.getPsiFile(dw) }

      //    affectedMarkers.asSequence().mapNotNull { it.host }.firstOrNull()?.let {host ->
      //      val injectedPsiFiles = injectedLanguageManager.getInjectedPsiFiles(host)?.map { it.first }.orEmpty()
      println("injectedPsiFiles = $injectedPsiFiles")
      for (psiFile in injectedPsiFiles.filterIsInstance<PsiFile>()) {
        val shreds = InjectedLanguageUtil.getShreds(psiFile)
        println("shreds1 count = ${shreds.size} for $psiFile")
      }
      //    }

      myInjectedFile = injectedPsiFiles.first()
    }


    markers.clear()
    val markersFromShreds = getMarkersFromShreds(InjectedLanguageUtil.getShreds(myInjectedFile))
    println("markersFromShreds = $markersFromShreds")
    markers.addAll(markersFromShreds)


  }

  private val PsiLanguageInjectionHost.contentRange
    get() = ElementManipulators.getManipulator(this).getRangeInElement(this).shiftRight(textRange.startOffset)

  override fun tryReuse(newInjectedFile: PsiFile, newHostRange: TextRange): Boolean {
    println("tryReuse = $newInjectedFile")
    return super.tryReuse(newInjectedFile, newHostRange)
  }
}


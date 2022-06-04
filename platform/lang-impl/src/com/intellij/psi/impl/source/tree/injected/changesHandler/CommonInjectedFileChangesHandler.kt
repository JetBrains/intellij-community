// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected.changesHandler

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.parents
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*
import kotlin.Pair
import kotlin.math.max
import kotlin.math.min

open class CommonInjectedFileChangesHandler(
  shreds: List<PsiLanguageInjectionHost.Shred>,
  hostEditor: Editor,
  fragmentDocument: Document,
  injectedFile: PsiFile
) : BaseInjectedFileChangesHandler(hostEditor, fragmentDocument, injectedFile) {

  protected val markers: MutableList<MarkersMapping> =
    LinkedList<MarkersMapping>().apply {
      addAll(getMarkersFromShreds(shreds))
    }


  protected fun getMarkersFromShreds(shreds: List<PsiLanguageInjectionHost.Shred>): List<MarkersMapping> {
    val result = ArrayList<MarkersMapping>(shreds.size)

    val smartPointerManager = SmartPointerManager.getInstance(myProject)
    var currentOffsetInHostFile = -1
    var currentOffsetInInjectedFile = -1
    for (shred in shreds) {
      val rangeMarker = fragmentMarkerFromShred(shred)
      val rangeInsideHost = shred.rangeInsideHost
      val host = shred.host ?: failAndReport("host should not be null", null, null)
      val origMarker = myHostDocument.createRangeMarker(rangeInsideHost.shiftRight(host.textRange.startOffset))
      val elementPointer = smartPointerManager.createSmartPsiElementPointer(host)
      result.add(MarkersMapping(origMarker, rangeMarker, elementPointer))

      origMarker.isGreedyToRight = true
      rangeMarker.isGreedyToRight = true
      if (origMarker.startOffset > currentOffsetInHostFile) {
        origMarker.isGreedyToLeft = true
      }
      if (rangeMarker.startOffset > currentOffsetInInjectedFile) {
        rangeMarker.isGreedyToLeft = true
      }
      currentOffsetInHostFile = origMarker.endOffset
      currentOffsetInInjectedFile = rangeMarker.endOffset
    }
    return result
  }

  protected open fun rebuildMarkers(contextRange: TextRange) {
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
        .filter { (_, range) -> injectionRange.intersects(range) }
        .mapNotNull { it.first as? PsiFile }
        .firstOrNull()
    }
    LOG.debug { "newInjectedFile = $newInjectedFile" }

    myInjectedFile = newInjectedFile ?: myInjectedFile

    //some hostless shreds could exist for keeping guarded values
    if (myInjectedFile.isValid) {
      markers.forEach { it.dispose() }
      markers.clear()

      val hostfulShreds = InjectedLanguageUtil.getShreds(myInjectedFile).filter { it.host != null }
      val markersFromShreds = getMarkersFromShreds(hostfulShreds)
      markers.addAll(markersFromShreds)
    }
    else
      LOG.error(
        getReportException("failed to rebuildMarkers at range $contextRange due to invalid myInjectedFile, newInjectedFile = $newInjectedFile", null, null))
  }

  override fun isValid(): Boolean = myInjectedFile.isValid && markers.all { it.isValid() }

  override fun commitToOriginal(e: DocumentEvent) {
    val text = myFragmentDocument.text
    LOG.debug { "commitToOriginal: $e text='${text.esclbr()}'" }
    val map = markers.groupByTo(LinkedHashMap()) { it.host }

    val documentManager = PsiDocumentManager.getInstance(myProject)
    documentManager.commitDocument(myHostDocument) // commit here and after each manipulator update
    var workingRange: TextRange? = null
    for (host in map.keys) {
      if (host == null) continue
      val hostRange = host.textRange
      val hostOffset = hostRange.startOffset
      var currentHost = host;
      val hostMarkers = map[host].orEmpty().reversed()
      for ((hostMarker, fragmentMarker, _) in hostMarkers) {
        val localInsideHost = ProperTextRange(hostMarker.startOffset - hostOffset, hostMarker.endOffset - hostOffset)
        val localInsideFile = ProperTextRange(fragmentMarker.startOffset, fragmentMarker.endOffset)
        currentHost!! // should never happen

        // fixme we could optimize here and check if host text has been changed and update only really changed fragments, not all of them
        if (localInsideFile.endOffset <= text.length && !localInsideFile.isEmpty) {
          val decodedText = localInsideFile.substring(text)
          currentHost = updateHostOrFail(currentHost, localInsideHost, decodedText, e)
        }
        else if (hostMarkers.size == 1 && text.isEmpty()) {
          currentHost = updateHostOrFail(currentHost, localInsideHost, text, e)
        }
        else
          continue
        workingRange = workingRange union currentHost.contentRange
      }
    }

    if (!markers.all { it.isValid() }) {
      workingRange?.let { workingRange ->
        LOG.logMarkers("before rebuild")
        rebuildMarkers(workingRange)
        LOG.logMarkers("after rebuild")
      }
    }
    else
      LOG.logMarkers("markers were not rebuilt")
  }

  private fun updateHostOrFail(currentHost: PsiLanguageInjectionHost,
                               localInsideHost: TextRange,
                               decodedText: String,
                               e: DocumentEvent?): PsiLanguageInjectionHost {
    LOG.debug { "updating host '${currentHost.text?.esclbr()}' at $localInsideHost with '${decodedText.esclbr()}' " }
    val updatedHost = updateHostElement(currentHost, localInsideHost, decodedText)
    if (updatedHost == null)
      failAndReport("Updating host returned null. Original host" + currentHost +
                    "; original text: " + currentHost.text +
                    "; updated range in host: " + localInsideHost +
                    "; decoded text to replace: " + decodedText.esclbr(), e)
    LOG.debug { "updated host: '${updatedHost.text?.esclbr()}'" }
    return updatedHost
  }

  @Deprecated("use updateHostElement", ReplaceWith("updateHostElement"))
  @ApiStatus.ScheduledForRemoval
  protected fun updateInjectionHostElement(host: PsiLanguageInjectionHost,
                                           insideHost: ProperTextRange,
                                           content: String): PsiLanguageInjectionHost? {
    return updateHostElement(host, insideHost, content)
  }

  protected open fun updateHostElement(host: PsiLanguageInjectionHost,
                                  insideHost: TextRange,
                                  content: String): PsiLanguageInjectionHost? {
    return ElementManipulators.handleContentChange(host, insideHost, content)
  }

  override fun dispose() {
    markers.forEach(MarkersMapping::dispose)
    markers.clear()
    super.dispose()
  }

  override fun handlesRange(range: TextRange): Boolean {
    if (markers.isEmpty()) return false

    val hostRange = TextRange.create(markers[0].hostMarker.startOffset,
                                     markers[markers.size - 1].hostMarker.endOffset)
    return range.intersects(hostRange)
  }

  protected fun fragmentMarkerFromShred(shred: PsiLanguageInjectionHost.Shred): RangeMarker {
    if (!shred.innerRange.run { 0 <= startOffset && startOffset <= endOffset && endOffset <= myFragmentDocument.textLength }) {
      LOG.error("fragment and host diverged: startOffset = ${shred.innerRange.startOffset}," +
                " endOffset = ${shred.innerRange.endOffset}," +
                " textLength = ${myFragmentDocument.textLength}",
                Attachment("host", shred.host?.text?.esclbr() ?: "<null>"),
                Attachment("fragment document", this.myFragmentDocument.text.esclbr()),
                Attachment("markers", markers.joinToString("\n", transform = ::markerString))
      )
    }
    return myFragmentDocument.createRangeMarker(shred.innerRange)
  }

  protected fun failAndReport(@NonNls message: String, e: DocumentEvent? = null, exception: Exception? = null): Nothing =
    throw getReportException(message, e, exception)

  protected fun getReportException(@NonNls message: String,
                                   e: DocumentEvent?,
                                   exception: Exception?): RuntimeExceptionWithAttachments =
    RuntimeExceptionWithAttachments("${this.javaClass.simpleName}: $message (event = $e)," +
                                    " myInjectedFile.isValid = ${myInjectedFile.isValid}, isValid = $isValid",
                                    *listOfNotNull(
                                      Attachment("hosts", markers.mapNotNullTo(LinkedHashSet()) { it.host }
                                        .joinToString("\n\n") { it.text.esclbr() ?: "<null>" }),
                                      Attachment("markers", markers.logMarkersRanges()),
                                      Attachment("fragment document", this.myFragmentDocument.text.esclbr()),
                                      exception?.let { Attachment("exception", it) }
                                    ).toTypedArray()
    )

  protected fun String.esclbr(): String = StringUtil.escapeLineBreak(this)

  protected val RangeMarker.debugText: String
    get() = "$range'${
      try {
        document.getText(range)
      }
      catch (e: IndexOutOfBoundsException) {
        e.toString()
      }
    }'".esclbr()

  protected fun Logger.logMarkers(title: String) {
    this.debug { "logMarkers('$title'):${markers.size}\n" + markers.joinToString("\n", transform = ::markerString) }
  }

  protected fun markerString(m: MarkersMapping): String {
    val (hostMarker, fragmentMarker, _) = m
    return "${hostMarker.debugText}\t<-\t${fragmentMarker.debugText}"
  }

  protected fun Iterable<MarkersMapping>.logMarkersRanges(): String = joinToString("\n", transform = ::markerString)

  protected fun String.substringVerbose(start: Int, cursor: Int): String = try {
    substring(start, cursor)
  }
  catch (e: StringIndexOutOfBoundsException) {
    failAndReport("can't get substring ($start, $cursor) of '${this}'[$length]", exception = e)
  }

  fun distributeTextToMarkers(affectedMarkers: List<MarkersMapping>,
                              affectedRange: TextRange,
                              limit: Int): List<Pair<MarkersMapping, String>> {

    tailrec fun List<MarkersMapping>.nearestValidMarker(start: Int): MarkersMapping? {
      val next = getOrNull(start) ?: return null
      if (next.isValid()) return next
      return nearestValidMarker(start + 1)
    }

    var cursor = 0
    var remainder = 0
    return affectedMarkers.indices.map { i ->
      val marker = affectedMarkers[i]
      val fragmentMarker = marker.fragmentMarker

      marker to if (fragmentMarker.isValid) {
        val start = max(cursor, fragmentMarker.startOffset) - remainder
        remainder = 0
        val text = fragmentMarker.document.text
        val fragmentText = fragmentMarker.range.subSequence(text)
        val lastEndOfLine = fragmentText.lastIndexOf("\n")

        val nextValidMarker by lazy(LazyThreadSafetyMode.NONE) { affectedMarkers.nearestValidMarker(i + 1) }

        cursor = if (lastEndOfLine != -1 && lastEndOfLine != fragmentText.length - 1 && nextValidMarker != null) {
          remainder = fragmentText.length - lastEndOfLine - 1
          max(cursor, fragmentMarker.startOffset + lastEndOfLine + 1)
        }
        else if (affectedLength(marker, affectedRange) == 0 && affectedLength(nextValidMarker, affectedRange) > 1)
          nextValidMarker!!.fragmentMarker.startOffset
        else
          min(text.length, max(fragmentMarker.endOffset, limit))

        text.substringVerbose(start, cursor)
      }
      else ""
    }
  }

}

@Deprecated("Use platform API", ReplaceWith("debug", "com.intellij.openapi.diagnostic"))
inline fun Logger.debug(message: () -> String) {
  this.debug(null, message)
}

private val LOG = logger<CommonInjectedFileChangesHandler>()

data class MarkersMapping(val hostMarker: RangeMarker,
                          val fragmentMarker: RangeMarker,
                          val hostPointer: SmartPsiElementPointer<PsiLanguageInjectionHost>) {
  val host: PsiLanguageInjectionHost? get() = hostPointer.element
  val hostElementRange: TextRange? get() = hostPointer.range?.range
  val fragmentRange: TextRange get() = fragmentMarker.range
  fun isValid(): Boolean = hostMarker.isValid && fragmentMarker.isValid && hostPointer.element?.isValid == true
  fun dispose() {
    fragmentMarker.dispose()
    hostMarker.dispose()
  }
}

infix fun TextRange?.union(another: TextRange?) = another?.let { this?.union(it) ?: it } ?: this

inline val Segment.range: TextRange get() = TextRange.create(this)

inline val PsiLanguageInjectionHost.Shred.innerRange: TextRange
  get() = TextRange.create(this.range.startOffset + this.prefix.length,
                           this.range.endOffset - this.suffix.length)

val PsiLanguageInjectionHost.contentRange
  get() = ElementManipulators.getValueTextRange(this).shiftRight(textRange.startOffset)

private val PsiElement.withNextSiblings: Sequence<PsiElement>
  get() = generateSequence(this) { it.nextSibling }

@ApiStatus.Internal
fun getInjectionHostAtRange(hostPsiFile: PsiFile, contextRange: Segment): PsiLanguageInjectionHost? =
  hostPsiFile.findElementAt(contextRange.startOffset)?.withNextSiblings.orEmpty()
    .takeWhile { it.textRange.startOffset <= contextRange.endOffset }
    .flatMap { it.parents(true).take(3) }
    .filterIsInstance<PsiLanguageInjectionHost>().firstOrNull()

private fun affectedLength(markersMapping: MarkersMapping?, affectedRange: TextRange): Int =
  markersMapping?.fragmentRange?.let { affectedRange.intersection(it)?.length } ?: -1
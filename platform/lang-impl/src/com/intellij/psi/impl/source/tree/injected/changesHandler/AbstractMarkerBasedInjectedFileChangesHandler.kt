// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected.changesHandler

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.Trinity
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import kotlin.math.max
import kotlin.math.min

abstract class AbstractMarkerBasedInjectedFileChangesHandler(editor: Editor,
                                                             newDocument: Document,
                                                             injectedFile: PsiFile) :
  BaseInjectedFileChangesHandler(editor, newDocument, injectedFile) {

  protected abstract val markers: MutableList<Marker>

  protected fun localRangeMarkerFromShred(shred: PsiLanguageInjectionHost.Shred): RangeMarker = myNewDocument.createRangeMarker(
    shred.range.startOffset + shred.prefix.length,
    shred.range.endOffset - shred.suffix.length)

  protected fun rebuildLocalMarkersFromShreds() {
    val shreds = InjectedLanguageUtil.getShreds(myInjectedFile)
    if (markers.size != shreds.size) failAndReport("markers and shreds doesn't match")
    val newMarkers = ArrayList<Marker>(markers.size)
    for ((marker, shred) in markers.zip(shreds)) {

      val shredRange = with(shred) { host?.let { rangeInsideHost.shiftRight(it.textRange.startOffset) } }

      if (shredRange != marker.origin.range) {
        failAndReport("shred and marker doesn't match $shredRange != ${marker.origin.range}")
      }

      val newLocalMarker = localRangeMarkerFromShred(shred).apply {
        isGreedyToLeft = marker.local.isGreedyToLeft
        isGreedyToRight = marker.local.isGreedyToRight
      }

      marker.local.dispose()
      newMarkers.add(Marker(marker.origin, newLocalMarker, marker.third))
    }
    markers.clear()
    markers.addAll(newMarkers)
  }

  protected fun failAndReport(message: String, e: DocumentEvent? = null, exception: Exception? = null): Nothing =
    throw RuntimeExceptionWithAttachments("${this.javaClass.simpleName}: $message (event = $e)," +
                                          " myInjectedFile.isValid = ${myInjectedFile.isValid}, isValid = $isValid",
                                          *listOfNotNull(
                                            Attachment("host", markers.firstOrNull()?.host?.text ?: "<null>"),
                                            Attachment("markers", markers.toString()),
                                            Attachment("injected document", this.myNewDocument.text),
                                            exception?.let { Attachment("exception", it) }
                                          ).toTypedArray()
    )

  protected fun String.substringVerbose(start: Int, cursor: Int): String = try {
    substring(start, cursor)
  }
  catch (e: StringIndexOutOfBoundsException) {
    failAndReport("can't get substring ($start, $cursor) of '${this}'[$length]", exception = e)
  }

  fun mapMarkersToText(affectedMarkers: List<Marker>, affectedRange: TextRange, limit: Int): List<Pair<Marker, String>> {
    var cursor = 0
    return affectedMarkers.indices.map { i ->
      val marker = affectedMarkers[i]
      val localMarker = marker.local

      marker to if (localMarker.isValid) {
        val start = max(cursor, localMarker.startOffset)
        val text = localMarker.document.text
        cursor = if (affectedLength(marker, affectedRange) == 0 && affectedLength(affectedMarkers.getOrNull(i + 1), affectedRange) > 1)
          affectedMarkers.getOrNull(i + 1)!!.local.startOffset
        else
          min(text.length, max(localMarker.endOffset, limit))

        text.substringVerbose(start, cursor)
      }
      else ""
    }
  }


}

fun affectedLength(marker: Marker?, affectedRange: TextRange): Int =
  marker?.localRange?.let { affectedRange.intersection(it)?.length } ?: -1

typealias Marker = Trinity<RangeMarker, RangeMarker, SmartPsiElementPointer<PsiLanguageInjectionHost>>

inline val Marker.host: PsiLanguageInjectionHost? get() = this.third.element

inline val Marker.hostSegment: Segment? get() = this.third.range

inline val Marker.origin: RangeMarker get() = this.first

inline val Marker.local: RangeMarker get() = this.second

inline val Marker.localRange: TextRange get() = this.second.range

inline val Segment.range: TextRange get() = TextRange.create(this)
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.lang.Language
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.editor.impl.ManualRangeMarker
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * A utility class responsible for caching and updating text ranges in response to document changes.
 * Is NOT synchronized.
 */
internal class MarkerCache(
  private val myPointers: SmartPointerTracker
) {

  /**
   * temporary cache of ranges that is recreated and dropped on each document commit.
   */
  private var myMarkerRanges: MarkerRanges? = null

  private fun getUpdatedMarkers(frozen: FrozenDocument, events: List<DocumentEventImpl>): MarkerRanges {
    val eventCount = events.size
    assert(eventCount > 0)

    val cache = myMarkerRanges
    if (cache != null && cache.myEventCount == eventCount) return cache

    val answer = if (cache != null && cache.myEventCount < eventCount) {
      // apply only the new events
      cache.applyEvents(events.subList(cache.myEventCount, eventCount))
    }
    else {
      // cache is either missing or corresponds to a newer event (contains "future").
      // apply all events from scratch.
      val infos = myPointers.getSortedInfos()
      val markers = createMarkers(infos)
      val initial = MarkerRanges(0, frozen, infos, markers)
      initial.applyEvents(events)
    }

    myMarkerRanges = answer
    return answer
  }

  fun updateMarkers(frozen: FrozenDocument, events: List<DocumentEvent>): Boolean {
    @Suppress("UNCHECKED_CAST")
    val updated = getUpdatedMarkers(frozen, events as List<DocumentEventImpl>)

    var sorted = true
    for (i in updated.myMarkers.indices) {
      val info = updated.mySortedInfos[i]
      info.setRange(updated.myMarkers[i])
      if (sorted && i > 0 && INFO_COMPARATOR.compare(updated.mySortedInfos[i - 1], info) > 0) {
        sorted = false
      }
    }

    myMarkerRanges = null
    return sorted
  }

  /**
   * infers the up-to-date range for the given segment in the given containingFile, and the frozen document after applying the given events.
   */
  fun getUpdatedRange(info: SelfElementInfo, frozen: FrozenDocument, events: List<DocumentEvent>): TextRange? {
    @Suppress("UNCHECKED_CAST")
    val struct = getUpdatedMarkers(frozen, events as List<DocumentEventImpl>)
    val updated = struct.findManualMarker(info) ?: return null
    return UnfairTextRange(updated.startOffset, updated.endOffset)
  }

  fun rangeChanged() {
    myMarkerRanges = null
  }

  /**
   * An immutable cache of document state and marker ranges after applying a sequence of events.
   */
  private class MarkerRanges(
    /**
     * Number of events that have been applied to the document to get this state.
     * Zero corresponds to the currently committed document.
     */
    val myEventCount: Int,
    /**
     * The document state after applying [myEventCount] events.
     */
    val myResultDocument: FrozenDocument,
    /**
     * Sorted list of [SelfElementInfo] instances stored for this file.
     * Their state corresponds to the last committed document state, i.e., it is NOT in sync with [myResultDocument].
     * The list is sorted by [SelfElementInfoComparator].
     */
    val mySortedInfos: List<SelfElementInfo>,
    /**
     * Manual markers for [mySortedInfos].
     * Their state is updated by applying [myEventCount] events.
     */
    val myMarkers: Array<ManualRangeMarker?>,
  ) {
    /**
     * Applies the given events to the document state and updates the marker ranges.
     *
     * @return the updated cache after applying the given events.
     */
    fun applyEvents(events: List<DocumentEventImpl>): MarkerRanges {
      var frozen = myResultDocument
      val resultMarkers = myMarkers.clone()
      for (event in events) {
        val before = frozen
        frozen = frozen.applyEvent(event, 0)
        val correctedEvent = withFrozenDocument(event, frozen)

        var i = 0
        while (i < resultMarkers.size) {
          val currentRange = resultMarkers[i]

          var sameMarkersEnd = i + 1
          while (sameMarkersEnd < resultMarkers.size && resultMarkers[sameMarkersEnd] === currentRange) {
            sameMarkersEnd++
          }

          val updatedRange = currentRange?.getUpdatedRange(correctedEvent, before)
          while (i < sameMarkersEnd) {
            resultMarkers[i] = updatedRange
            i++
          }
        }
      }
      return MarkerRanges(myEventCount + events.size, frozen, mySortedInfos, resultMarkers)
    }

    /**
     * Finds the manual marker for the given [SelfElementInfo].
     */
    fun findManualMarker(info: SelfElementInfo): ManualRangeMarker? {
      val i = mySortedInfos.binarySearch(info, INFO_COMPARATOR)
      return if (i >= 0) myMarkers[i] else null
    }
  }

  /**
   * Orders [SelfElementInfo] instances for deterministic range-marker processing:
   *
   *  1. Entries with a valid range (non-negative start offset) before entries without a range;
   *  2. Ascending start offset;
   *  3. Ascending end offset when starts are equal;
   *  4. Non-greedy before greedy when ranges are identical.
   *
   */
  private class SelfElementInfoComparator : Comparator<SelfElementInfo> {
    override fun compare(info1: SelfElementInfo, info2: SelfElementInfo): Int {
      var o1 = info1.psiStartOffset
      var o2 = info2.psiStartOffset
      if (o1 < 0 || o2 < 0) return if (o1 >= 0) -1 else if (o2 >= 0) 1 else 0 // infos without range go after infos with range

      if (o1 != o2) return if (o1 > o2) 1 else -1

      o1 = info1.psiEndOffset
      o2 = info2.psiEndOffset
      if (o1 != o2) return if (o1 > o2) 1 else -1

      return (if (info1.isGreedy) 1 else 0) - (if (info2.isGreedy) 1 else 0)
    }
  }

  private class MockIdentikit : Identikit() {
    override fun findPsiElement(file: PsiFile, startOffset: Int, endOffset: Int): PsiElement? {
      return null
    }

    override fun getFileLanguage(): Language {
      throw IllegalStateException()
    }

    override fun isForPsiFile(): Boolean {
      return false
    }
  }

  companion object {
    val INFO_COMPARATOR: Comparator<SelfElementInfo> = SelfElementInfoComparator()

    private fun createMarkers(infos: List<SelfElementInfo>): Array<ManualRangeMarker?> {
      val markers = arrayOfNulls<ManualRangeMarker>(infos.size)
      var i = 0
      while (i < markers.size) {
        val info = infos[i]
        val greedy = info.isGreedy
        val start = info.psiStartOffset
        val end = info.psiEndOffset
        markers[i] = ManualRangeMarker(start, end, greedy, greedy, !greedy, null)

        i++
        while (i < markers.size && rangeEquals(infos[i], start, end, greedy)) {
          markers[i] = markers[i - 1]
          i++
        }
      }
      return markers
    }

    private fun rangeEquals(info: SelfElementInfo, start: Int, end: Int, greedy: Boolean): Boolean {
      return start == info.psiStartOffset && end == info.psiEndOffset && greedy == info.isGreedy
    }

    /**
     * infers the up-to-date range for the given segment in the given containingFile, and the frozen document after applying the given events.
     */
    fun getUpdatedRange(
      containingFile: PsiFile,
      segment: Segment,
      isSegmentGreedy: Boolean,
      frozen: FrozenDocument,
      events: List<DocumentEvent>,
    ): Segment? {
      @Suppress("UNCHECKED_CAST")
      val ranges = createInitialRangesForSegment(segment, isSegmentGreedy, frozen, events as List<DocumentEventImpl>, containingFile)

      // NB: convert events from completion to whole doc change event to more precise translation
      val newEvents = events.map { event ->
        if (isWholeDocumentReplace(frozen, event)) {
          withWholeTextReplaced(event)
        }
        else {
          event
        }
      }
      val updated = ranges.applyEvents(newEvents)
      return updated.myMarkers[0]
    }

    private fun createInitialRangesForSegment(
      segment: Segment,
      isSegmentGreedy: Boolean,
      frozen: FrozenDocument,
      events: List<DocumentEventImpl>,
      containingFile: PsiFile,
    ): MarkerRanges {
      // using a mock SelfElementInfo to infer updated range for the segment
      val info = SelfElementInfo(ProperTextRange.create(segment), MockIdentikit(), containingFile, isSegmentGreedy)
      val infos = listOf(info)

      val greedy = info.isGreedy
      val start = info.psiStartOffset
      val end = info.psiEndOffset
      val surviveOnExternalChange = events.any { event -> isWholeDocumentReplace(frozen, event) }
      val marker = ManualRangeMarker(start, end, greedy, greedy, surviveOnExternalChange, null)

      return MarkerRanges(0, frozen, infos, arrayOf(marker))
    }

    private fun isWholeDocumentReplace(frozen: FrozenDocument, event: DocumentEventImpl): Boolean {
      return event.initialStartOffset == 0 && event.initialOldLength == frozen.textLength
    }

    private fun withWholeTextReplaced(event: DocumentEventImpl): DocumentEventImpl {
      return DocumentEventImpl(
        event.document,
        event.offset,
        event.oldFragment,
        event.newFragment,
        event.oldTimeStamp,
        true,
        event.initialStartOffset,
        event.initialOldLength,
        event.moveOffset
      )
    }

    private fun withFrozenDocument(event: DocumentEventImpl, frozen: FrozenDocument): DocumentEventImpl {
      return DocumentEventImpl(
        frozen,
        event.offset,
        event.oldFragment,
        event.newFragment,
        event.oldTimeStamp,
        event.isWholeTextReplaced,
        event.initialStartOffset,
        event.initialOldLength,
        event.moveOffset
      )
    }
  }
}

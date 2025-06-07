// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals

import andel.intervals.impl.AnchorStorageSerializer
import andel.editor.AnchorId
import andel.editor.RangeMarkerId
import andel.operation.NewOffsetProvider
import andel.operation.Operation
import andel.operation.Sticky
import andel.operation.edit
import andel.text.Text
import andel.text.TextRange
import fleet.util.UID
import kotlinx.serialization.Serializable

private interface AnchorsQuery {
  fun resolveAnchor(anchorId: AnchorId): Long?
  fun resolveRangeMarker(markerId: RangeMarkerId): TextRange?
}

@Serializable(with = AnchorStorageSerializer::class)
data class AnchorStorage(val intervals: Intervals<UID, Unit>): AnchorsQuery {
  companion object {
    fun empty(): AnchorStorage = AnchorStorage(intervals = Intervals.keepingCollapsed().keyed<UID>().empty())
  }

  override fun resolveAnchor(anchorId: AnchorId): Long? {
    val interval = intervals.findById(anchorId.id)
    if (interval == null)
      return null
    require(interval.from == interval.to) {
      "nonempty interval found for anchor $anchorId"
    }
    return interval.from
  }

  override fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? {
    return intervals.findById(markerId.id)?.let { TextRange(it.from, it.to) }
  }

  fun addAnchor(anchorId: AnchorId, offset: Long, sticky: Sticky): AnchorStorage {
    val intervalToAdd = Interval(anchorId.id, offset, offset, sticky == Sticky.LEFT, sticky == Sticky.RIGHT, Unit)
    return this.copy(
      intervals = intervals.addIntervals(listOf(intervalToAdd)),
    )
  }

  fun addRangeMarker(markerId: RangeMarkerId, from: Long, to: Long, closedLeft: Boolean, closedRight: Boolean): AnchorStorage {
    val intervalToAdd = Interval(markerId.id, from, to, closedLeft, closedRight, Unit)
    return this.copy(
      intervals = intervals.addIntervals(listOf(intervalToAdd)),
    )
  }

  fun removeAnchor(anchorId: AnchorId): AnchorStorage {
    return this.copy(
      intervals = intervals.removeByIds(listOf(anchorId.id)),
    )
  }

  fun removeRangeMarker(rangeMarkerId: RangeMarkerId): AnchorStorage {
    return this.copy(
      intervals = intervals.removeByIds(listOf(rangeMarkerId.id)),
    )
  }

  fun edit(before: Text, after: Text, edit: Operation): AnchorStorage {
    return this.copy(intervals = intervals.edit(edit))
  }

  fun edit(newOffsetProvider: NewOffsetProvider): AnchorStorage {
    return this.copy(intervals = intervals.edit(newOffsetProvider))
  }

  fun batchUpdate(anchorIds: List<AnchorId>, anchorOffsets: LongArray,
                  rangeIds: List<RangeMarkerId>, ranges: List<TextRange>): AnchorStorage {
    val idsToRemove = anchorIds.map { it.id } + rangeIds.map { it.id }
    val intervalsToAdd =
      anchorIds.mapIndexed { i, anchorId -> Interval(anchorId.id, anchorOffsets[i], anchorOffsets[i], true, false, Unit) } +
      rangeIds.mapIndexed { i, rangeId -> Interval(rangeId.id, ranges[i].start, ranges[i].end, false, false, Unit) }
    return this.copy(intervals = intervals.removeByIds(idsToRemove).addIntervals(intervalsToAdd))
  }
}
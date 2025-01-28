// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals.impl

import andel.intervals.*
import fleet.util.UID
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable

internal object AnchorStorageSerializer : DataSerializer<AnchorStorage, AnchorStorageSerializer.AnchorStorageData>(AnchorStorageData.serializer()) {

  @Serializable
  data class IntervalData(val id: UID,
                          val from: Long,
                          val to: Long,
                          val closedLeft: Boolean,
                          val closedRight: Boolean)

  @Serializable
  data class AnchorStorageData(val intervals: List<IntervalData>)

  override fun fromData(data: AnchorStorageData): AnchorStorage =
    AnchorStorage(
      Intervals.keepingCollapsed().fromIntervals(
        data.intervals.map { i ->
          Interval(id = i.id,
                   from = i.from,
                   to = i.to,
                   greedyLeft = i.closedLeft,
                   greedyRight = i.closedRight,
                   data = Unit)
        })
    )

  override fun toData(value: AnchorStorage): AnchorStorageData =
    AnchorStorageData(
      value.intervals.query(0, Long.MAX_VALUE).map { i ->
        IntervalData(
          id = i.id,
          from = i.from,
          to = i.to,
          closedLeft = i.greedyLeft,
          closedRight = i.greedyRight)
      }.toList()
    )
}
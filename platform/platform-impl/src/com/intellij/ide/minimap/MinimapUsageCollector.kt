// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MinimapUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("editor.minimap", 3)

  override fun getGroup(): EventLogGroup = GROUP

  enum class ToggleSource {
    ACTION_TOGGLE,
    ACTION_ENABLE,
    ACTION_DISABLE,
    SETTINGS,
  }

  enum class DragDistanceBucket {
    PX_0_200,
    PX_201_800,
    PX_801_2000,
    PX_2001_PLUS,
  }

  enum class ScrollDirection {
    UP,
    DOWN,
  }

  private val SCALE_MODE = EventFields.Enum<MinimapScaleMode>("scale_mode")
  private val RIGHT_ALIGNED = EventFields.Boolean("right_aligned")
  private val TOGGLE_SOURCE = EventFields.Enum<ToggleSource>("source")
  private val DRAG_DISTANCE = EventFields.Enum<DragDistanceBucket>("drag_distance")
  private val SCROLL_DIRECTION = EventFields.Enum<ScrollDirection>("direction")

  private val TOGGLED = GROUP.registerVarargEvent(
    "toggled",
    EventFields.Enabled,
    TOGGLE_SOURCE,
    SCALE_MODE,
    RIGHT_ALIGNED,
  )

  private val CLICKED = GROUP.registerVarargEvent(
    "clicked",
    SCALE_MODE,
    RIGHT_ALIGNED,
    EventFields.FileType,
  )

  private val DRAGGED = GROUP.registerVarargEvent(
    "dragged",
    SCALE_MODE,
    RIGHT_ALIGNED,
    DRAG_DISTANCE,
    EventFields.FileType,
  )

  private val WHEEL_SCROLLED = GROUP.registerVarargEvent(
    "wheel.scrolled",
    SCALE_MODE,
    SCROLL_DIRECTION,
    EventFields.FileType,
  )

  private val HOVER_SHOWN = GROUP.registerEvent(
    "hover.shown",
    SCALE_MODE,
  )

  @JvmStatic
  fun logToggled(
    enabled: Boolean,
    source: ToggleSource,
    scaleMode: MinimapScaleMode,
    rightAligned: Boolean,
  ) {
    TOGGLED.log(
      EventFields.Enabled.with(enabled),
      TOGGLE_SOURCE.with(source),
      SCALE_MODE.with(scaleMode),
      RIGHT_ALIGNED.with(rightAligned),
    )
  }

  @JvmStatic
  fun logClicked(
    scaleMode: MinimapScaleMode,
    rightAligned: Boolean,
    fileType: FileType? = null,
  ) {
    CLICKED.log(
      SCALE_MODE.with(scaleMode),
      RIGHT_ALIGNED.with(rightAligned),
      EventFields.FileType.with(fileType),
    )
  }

  @JvmStatic
  fun logDragged(
    scaleMode: MinimapScaleMode,
    rightAligned: Boolean,
    dragDistanceBucket: DragDistanceBucket,
    fileType: FileType? = null,
  ) {
    DRAGGED.log(
      SCALE_MODE.with(scaleMode),
      RIGHT_ALIGNED.with(rightAligned),
      DRAG_DISTANCE.with(dragDistanceBucket),
      EventFields.FileType.with(fileType),
    )
  }

  @JvmStatic
  fun logWheelScrolled(
    scaleMode: MinimapScaleMode,
    direction: ScrollDirection,
    fileType: FileType? = null,
  ) {
    WHEEL_SCROLLED.log(
      SCALE_MODE.with(scaleMode),
      SCROLL_DIRECTION.with(direction),
      EventFields.FileType.with(fileType),
    )
  }

  @JvmStatic
  fun logHoverShown(
    scaleMode: MinimapScaleMode,
  ) {
    HOVER_SHOWN.log(scaleMode)
  }

  @JvmStatic
  fun toDragDistanceBucket(distancePx: Int): DragDistanceBucket {
    val normalizedDistance = distancePx.coerceAtLeast(0)
    return when {
      normalizedDistance <= 200 -> DragDistanceBucket.PX_0_200
      normalizedDistance <= 800 -> DragDistanceBucket.PX_201_800
      normalizedDistance <= 2000 -> DragDistanceBucket.PX_801_2000
      else -> DragDistanceBucket.PX_2001_PLUS
    }
  }
}

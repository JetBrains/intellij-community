// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MinimapUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("editor.minimap", 1)

  override fun getGroup(): EventLogGroup = GROUP

  private enum class MinimapMode {
    LEGACY,
    NEW,
  }

  enum class ToggleSource {
    ACTION_TOGGLE,
    ACTION_ENABLE,
    ACTION_DISABLE,
    SETTINGS,
  }

  enum class InteractionSource {
    MOUSE,
    TOUCHPAD,
    UNKNOWN,
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

  enum class HoverTargetType {
    BREAKPOINT,
    STRUCTURE,
    UNKNOWN,
  }

  private val MODE = EventFields.Enum<MinimapMode>("mode")
  private val SCALE_MODE = EventFields.Enum<MinimapScaleMode>("scale_mode")
  private val RIGHT_ALIGNED = EventFields.Boolean("right_aligned")
  private val TOGGLE_SOURCE = EventFields.Enum<ToggleSource>("source")
  private val INTERACTION_SOURCE = EventFields.Enum<InteractionSource>("input_source")
  private val DRAG_DISTANCE = EventFields.Enum<DragDistanceBucket>("drag_distance")
  private val SCROLL_DIRECTION = EventFields.Enum<ScrollDirection>("direction")
  private val HOVER_TARGET = EventFields.Enum<HoverTargetType>("target_type")

  private val TOGGLED = GROUP.registerVarargEvent(
    "toggled",
    EventFields.Enabled,
    TOGGLE_SOURCE,
    MODE,
    SCALE_MODE,
    RIGHT_ALIGNED,
  )

  private val CLICKED = GROUP.registerVarargEvent(
    "clicked",
    MODE,
    SCALE_MODE,
    RIGHT_ALIGNED,
    INTERACTION_SOURCE,
  )

  private val DRAGGED = GROUP.registerVarargEvent(
    "dragged",
    MODE,
    SCALE_MODE,
    RIGHT_ALIGNED,
    DRAG_DISTANCE,
    INTERACTION_SOURCE,
  )

  private val WHEEL_SCROLLED = GROUP.registerVarargEvent(
    "wheel.scrolled",
    MODE,
    SCALE_MODE,
    SCROLL_DIRECTION,
    INTERACTION_SOURCE,
  )

  private val HOVER_SHOWN = GROUP.registerEvent(
    "hover.shown",
    MODE,
    SCALE_MODE,
    HOVER_TARGET,
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
      MODE.with(mode()),
      SCALE_MODE.with(scaleMode),
      RIGHT_ALIGNED.with(rightAligned),
    )
  }

  @JvmStatic
  fun logClicked(
    scaleMode: MinimapScaleMode,
    rightAligned: Boolean,
    source: InteractionSource = InteractionSource.UNKNOWN,
  ) {
    CLICKED.log(
      MODE.with(mode()),
      SCALE_MODE.with(scaleMode),
      RIGHT_ALIGNED.with(rightAligned),
      INTERACTION_SOURCE.with(source),
    )
  }

  @JvmStatic
  fun logDragged(
    scaleMode: MinimapScaleMode,
    rightAligned: Boolean,
    dragDistanceBucket: DragDistanceBucket,
    source: InteractionSource = InteractionSource.UNKNOWN,
  ) {
    DRAGGED.log(
      MODE.with(mode()),
      SCALE_MODE.with(scaleMode),
      RIGHT_ALIGNED.with(rightAligned),
      DRAG_DISTANCE.with(dragDistanceBucket),
      INTERACTION_SOURCE.with(source),
    )
  }

  @JvmStatic
  fun logWheelScrolled(
    scaleMode: MinimapScaleMode,
    direction: ScrollDirection,
    source: InteractionSource = InteractionSource.UNKNOWN,
  ) {
    WHEEL_SCROLLED.log(
      MODE.with(mode()),
      SCALE_MODE.with(scaleMode),
      SCROLL_DIRECTION.with(direction),
      INTERACTION_SOURCE.with(source),
    )
  }

  @JvmStatic
  fun logHoverShown(
    scaleMode: MinimapScaleMode,
    targetType: HoverTargetType,
  ) {
    HOVER_SHOWN.log(mode(), scaleMode, targetType)
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

  private fun mode(): MinimapMode = if (MinimapRegistry.isLegacy()) MinimapMode.LEGACY else MinimapMode.NEW
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup

import com.intellij.ui.awt.AnchoredPoint
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Point

/**
 * A set of options to determine where and how a popup should be shown.
 *
 * An instance is created using one of the static builders, currently only [aboveComponent].
 * The created options may then be modified using interface methods.
 */
sealed interface PopupShowOptions {
  val screenX: Int

  val screenY: Int

  val popupComponentGap: Int
  /**
   * The gap between the popup and the component.
   *
   * Currently only supported for [aboveComponent].
   * The gap must be in unscaled pixels.
   */
  fun withPopupComponentUnscaledGap(popupComponentGap: Int?): PopupShowOptions

  /**
   * The minimum popup height.
   *
   * Currently only supported for [aboveComponent].
   * The popup itself must also support height changing (e.g., by showing a vertical scrollbar).
   */
  fun withMinimumHeight(minimumHeight: Int?): PopupShowOptions

  fun withDefaultPopupComponentUnscaledGap(popupComponentGap: Int): PopupShowOptions

  fun withComponentPoint(componentPoint: AnchoredPoint): PopupShowOptions

  fun withScreenXY(screenX: Int, screenY: Int): PopupShowOptions

  fun withForcedXY(considerForcedXY: Boolean): PopupShowOptions

  fun withDefaultPopupAnchor(popupAnchor: AnchoredPoint.Anchor): PopupShowOptions

  fun withRelativePosition(relativePosition: PopupRelativePosition): PopupShowOptions

  companion object {

    /**
     * Creates popup options to show the popup above a specific component and left-aligned with it.
     */
    @JvmStatic
    fun aboveComponent(
      component: Component,
    ): PopupShowOptions {
      return PopupShowOptionsBuilder()
        .withComponentPoint(AnchoredPoint(AnchoredPoint.Anchor.TOP_LEFT, component))
        .withRelativePosition(PopupRelativePosition.TOP)
        .withDefaultPopupAnchor(AnchoredPoint.Anchor.BOTTOM_LEFT)
        .withDefaultPopupComponentUnscaledGap(4)
    }

    /**
     * Creates popup options to show the popup above a specific component and right-aligned with it.
     */
    @JvmStatic
    fun aboveComponentRightAligned(
      component: Component,
    ): PopupShowOptions {
      return PopupShowOptionsBuilder()
        .withComponentPoint(AnchoredPoint(AnchoredPoint.Anchor.TOP_RIGHT, component))
        .withRelativePosition(PopupRelativePosition.TOP)
        .withDefaultPopupAnchor(AnchoredPoint.Anchor.BOTTOM_RIGHT)
        .withDefaultPopupComponentUnscaledGap(4)
    }

    /**
     * Creates popup options to show the popup blow a specific component and left-aligned with it.
     */
    @JvmStatic
    fun belowComponent(
      component: Component,
    ): PopupShowOptions {
      return PopupShowOptionsBuilder()
        .withComponentPoint(AnchoredPoint(AnchoredPoint.Anchor.BOTTOM_LEFT, component))
        .withRelativePosition(PopupRelativePosition.BOTTOM)
        .withDefaultPopupAnchor(AnchoredPoint.Anchor.TOP_LEFT)
    }

    /**
     * Creates popup options to show the popup blow a specific component and right-aligned with it.
     */
    @JvmStatic
    fun belowComponentRightAligned(
      component: Component,
    ): PopupShowOptions {
      return PopupShowOptionsBuilder()
        .withComponentPoint(AnchoredPoint(AnchoredPoint.Anchor.BOTTOM_RIGHT, component))
        .withRelativePosition(PopupRelativePosition.BOTTOM)
        .withDefaultPopupAnchor(AnchoredPoint.Anchor.TOP_RIGHT)
    }

    /**
     * Creates popup options to show the popup at the specified screen point.
     */
    @JvmStatic
    fun atScreenLocation(owner: Component, screenX: Int, screenY: Int, forced: Boolean): PopupShowOptions {
      return PopupShowOptionsBuilder()
        .withOwner(owner)
        .withScreenXY(screenX, screenY)
        .withForcedXY(forced)
    }
  }
}

@ApiStatus.Internal
class PopupShowOptionsBuilder : PopupShowOptions {
  private var owner: Component? = null
  private var screenPoint: Point? = null
  private var considerForcedXY: Boolean = false
  private var ownerAnchor: AnchoredPoint.Anchor? = null
  private var popupAnchor: AnchoredPoint.Anchor? = null
  private var relativePosition: PopupRelativePosition? = null
  private var popupComponentUnscaledGap: Int? = null
  private var minimumHeight: Int? = null

  override val screenX: Int get() = screenPoint?.x ?: -1
  override val screenY: Int get() = screenPoint?.y ?: -1

  override val popupComponentGap: Int
    get() = JBUI.scale(popupComponentUnscaledGap ?: 0)

  fun withOwner(owner: Component): PopupShowOptionsBuilder = apply {
    this.owner = owner
  }

  override fun withComponentPoint(componentPoint: AnchoredPoint): PopupShowOptionsBuilder = withOwner(componentPoint.component).apply {
    this.screenPoint = componentPoint.screenPoint
    this.ownerAnchor = componentPoint.anchor
  }

  override fun withScreenXY(screenX: Int, screenY: Int): PopupShowOptionsBuilder = apply {
    this.screenPoint = if (screenX == -1 && screenY == -1) null else Point(screenX, screenY)
  }

  override fun withForcedXY(considerForcedXY: Boolean): PopupShowOptionsBuilder = apply {
    this.considerForcedXY = considerForcedXY
  }

  override fun withDefaultPopupAnchor(popupAnchor: AnchoredPoint.Anchor): PopupShowOptionsBuilder = apply {
    if (this.popupAnchor == null) {
      this.popupAnchor = popupAnchor
    }
  }

  override fun withRelativePosition(relativePosition: PopupRelativePosition): PopupShowOptionsBuilder = apply {
    this.relativePosition = relativePosition
  }

  override fun withMinimumHeight(minimumHeight: Int?): PopupShowOptionsBuilder = apply {
    this.minimumHeight = minimumHeight
  }

  override fun withPopupComponentUnscaledGap(popupComponentGap: Int?): PopupShowOptionsBuilder = apply {
    this.popupComponentUnscaledGap = popupComponentGap
  }

  override fun withDefaultPopupComponentUnscaledGap(popupComponentGap: Int): PopupShowOptionsBuilder = apply {
    if (this.popupComponentUnscaledGap == null) {
      this.popupComponentUnscaledGap = popupComponentGap
    }
  }

  fun build(): PopupShowOptionsImpl {
    val owner = owner
    checkNotNull(owner) { "Owner is not set" }
    return PopupShowOptionsImpl(
      owner = owner,
      screenPoint = screenPoint,
      considerForcedXY = considerForcedXY,
      ownerAnchor = ownerAnchor,
      popupAnchor = popupAnchor ?: AnchoredPoint.Anchor.TOP_LEFT,
      relativePosition = relativePosition,
      popupComponentUnscaledGap = popupComponentUnscaledGap ?: 0,
      minimumHeight = minimumHeight,
    )
  }
}

@ApiStatus.Internal
data class PopupShowOptionsImpl(
  val owner: Component,
  val screenPoint: Point?,
  val considerForcedXY: Boolean,
  val ownerAnchor: AnchoredPoint.Anchor?,
  val popupAnchor: AnchoredPoint.Anchor,
  val relativePosition: PopupRelativePosition?,
  val popupComponentUnscaledGap: Int,
  val minimumHeight: Int?,
) {
  val screenX: Int get() = screenPoint?.x ?: -1
  val screenY: Int get() = screenPoint?.y ?: -1
}

enum class PopupRelativePosition {
  LEFT,
  RIGHT,
  TOP,
  BOTTOM,
}

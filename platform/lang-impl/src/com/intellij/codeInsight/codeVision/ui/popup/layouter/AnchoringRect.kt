@file:ApiStatus.Internal

package com.intellij.codeInsight.codeVision.ui.popup.layouter

import com.jetbrains.rd.util.reactive.IPropertyView
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@ApiStatus.Internal
enum class Anchoring {
  /**
   * Left or top, anchored outside the area.
   */
  NearOutside,

  /**
   * Left or top, anchored inside the area.
   */
  NearInside,

  /**
   * Center or middle, anchored inside the area. Preferably, in the center.
   */
  MiddleInside,

  /**
   * Right or bottom, anchored inside the area.
   */
  FarInside,

  /**
   * Right or bottom, anchored outside the area.
   */
  FarOutside
}

val Anchoring.isFar: Boolean get() = this == Anchoring.FarInside || this == Anchoring.FarOutside
val Anchoring.isNear: Boolean get() = this == Anchoring.NearInside || this == Anchoring.NearOutside
val Anchoring.isInside: Boolean get() = this == Anchoring.NearInside || this == Anchoring.FarInside || this == Anchoring.MiddleInside
val Anchoring.isOutside: Boolean get() = isInside.not()

@ApiStatus.Internal
data class Anchoring2D(val horizontal: Anchoring, val vertical: Anchoring)

val Anchoring2D.isInside: Boolean get() = vertical.isInside && horizontal.isInside

@ApiStatus.Internal
interface AnchoringRect {
  val rectangle: IPropertyView<Rectangle?>
}


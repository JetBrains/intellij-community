// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.design

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi

@Suppress("unused")
@Serializable
@ExperimentalIconsApi
class IconAlign(
  val verticalAlign: IconVerticalAlign,
  val horizontalAlign: IconHorizontalAlign
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IconAlign

    if (verticalAlign != other.verticalAlign) return false
    if (horizontalAlign != other.horizontalAlign) return false

    return true
  }

  override fun hashCode(): Int {
    var result = verticalAlign.hashCode()
    result = 31 * result + horizontalAlign.hashCode()
    return result
  }

  override fun toString(): String {
    return "IconAlign(verticalAlign=$verticalAlign, horizontalAlign=$horizontalAlign)"
  }

  companion object {
    val TopLeft: IconAlign = IconAlign(IconVerticalAlign.Top, IconHorizontalAlign.Left)
    val TopCenter: IconAlign = IconAlign(IconVerticalAlign.Top, IconHorizontalAlign.Center)
    val TopRight: IconAlign = IconAlign(IconVerticalAlign.Top, IconHorizontalAlign.Right)
    val CenterLeft: IconAlign = IconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Left)
    val Center: IconAlign = IconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Center)
    val CenterRight: IconAlign = IconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Right)
    val BottomLeft: IconAlign = IconAlign(IconVerticalAlign.Bottom, IconHorizontalAlign.Left)
    val BottomCenter: IconAlign = IconAlign(IconVerticalAlign.Bottom, IconHorizontalAlign.Center)
    val BottomRight: IconAlign = IconAlign(IconVerticalAlign.Bottom, IconHorizontalAlign.Right)
  }
}

@Serializable
enum class IconVerticalAlign {
  Top,
  Center,
  Bottom
}

@Serializable
enum class IconHorizontalAlign {
  Left,
  Center,
  Right
}
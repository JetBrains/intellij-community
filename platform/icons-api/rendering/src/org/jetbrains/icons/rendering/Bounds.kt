// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.InternalIconsApi

@InternalIconsApi
class Bounds(
  val x: Int,
  val y: Int,
  width: Int,
  height: Int
): Dimensions(width, height) {


  fun copy(
    x: Int = this.x,
    y: Int = this.y,
    width: Int = this.width,
    height: Int = this.height
  ): Bounds {
    return Bounds(
      x,
      y,
      width,
      height
    )
  }

  fun canFit(other: Bounds): Boolean = other.width <= width && other.height <= height

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Bounds

    if (x != other.x) return false
    if (y != other.y) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + x
    result = 31 * result + y
    return result
  }

  override fun toString(): String {
    return "Bounds(x=$x, y=$y, width=$width, height=$height)"
  }


}

@ExperimentalIconsApi
open class Dimensions(
  val width: Int,
  val height: Int
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Dimensions

    if (width != other.width) return false
    if (height != other.height) return false

    return true
  }

  override fun hashCode(): Int {
    var result = width
    result = 31 * result + height
    return result
  }

  override fun toString(): String {
    return "Dimensions(width=$width, height=$height)"
  }
}
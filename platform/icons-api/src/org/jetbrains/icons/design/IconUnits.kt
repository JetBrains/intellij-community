// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.design

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi

/**
 * Samples:
 * <pre>
 *   20.px
 *  * 100.percent
 *  * 0.5.fraction
 *  * 5.dp
 *  * AutoIconUnit - fills max width
 * </pre>
 */
@Serializable
@ExperimentalIconsApi
sealed interface IconUnit {
  companion object {
    val Zero: IconUnit = 0.dp
    val Auto: IconUnit = MaxIconUnit
  }
}

@Serializable
@ExperimentalIconsApi
class DisplayPointIconUnit(val value: Double) : IconUnit {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DisplayPointIconUnit

    return value == other.value
  }

  override fun hashCode(): Int {
    return value.hashCode()
  }

  override fun toString(): String {
    return "DisplayPointIconUnit(value=$value)"
  }

}

@Serializable
@ExperimentalIconsApi
class PixelIconUnit(val value: Int) : IconUnit {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PixelIconUnit

    return value == other.value
  }

  override fun hashCode(): Int {
    return value
  }

  override fun toString(): String {
    return "PixelIconUnit(value=$value)"
  }
}

@Serializable
@ExperimentalIconsApi
class PercentIconUnit(val value: Double) : IconUnit {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PercentIconUnit

    return value == other.value
  }

  override fun hashCode(): Int {
    return value.hashCode()
  }

  override fun toString(): String {
    return "PercentIconUnit(value=$value)"
  }
}

@Serializable
@ExperimentalIconsApi
object MaxIconUnit : IconUnit

val Int.dp: DisplayPointIconUnit
  get() = DisplayPointIconUnit(this.toDouble())

val Double.dp: DisplayPointIconUnit
  get() = DisplayPointIconUnit(this)

val Float.dp: DisplayPointIconUnit
  get() = DisplayPointIconUnit(this.toDouble())

val Int.px: PixelIconUnit
  get() = PixelIconUnit(this)

/**
 * The resulting value is divided by 100, so the number before this represents actual percentage (not fraction)
 * @see fraction
 */
val Int.percent: PercentIconUnit
  get() = this.toDouble().percent

/**
 * The resulting value is divided by 100, so the number before this represents actual percentage (not fraction)
 * @see fraction
 */
val Double.percent: PercentIconUnit
  get() = PercentIconUnit(this / 100.0)

/**
 * The resulting value is divided by 100, so the number before this represents actual percentage (not fraction)
 * @see fraction
 */
val Float.percent: PercentIconUnit
  get() = this.toDouble().percent
/**
 * The resulting value is not divided by 100, acts as a percentage of bounds
 * @see percent
 */
val Int.fraction: PercentIconUnit
  get() = this.toDouble().fraction

/**
 * The resulting value is not divided by 100, acts as a percentage of bounds
 * @see percent
 */
val Double.fraction: PercentIconUnit
  get() = PercentIconUnit(this)

/**
 * The resulting value is not divided by 100, acts as a percentage of bounds
 * @see percent
 */
val Float.fraction: PercentIconUnit
  get() = this.toDouble().fraction

infix fun PixelIconUnit.relativeTo(bound: PixelIconUnit): PercentIconUnit {
  return (this.value / bound.value).fraction
}

infix fun DisplayPointIconUnit.relativeTo(bound: DisplayPointIconUnit): PercentIconUnit {
  return (this.value / bound.value).fraction
}
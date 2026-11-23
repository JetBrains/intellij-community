// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.builder.impl.checkNonNegative
import org.jetbrains.annotations.ApiStatus

/**
 * Defines top and bottom gaps. Values must be provided unscaled
 */
interface UnscaledGapsY {
  companion object {
    @JvmField
    val EMPTY: UnscaledGapsY = EmptyGapsY

    // a private nested class, so that only the static initializer of UnscaledGapsY initializes it
    private object EmptyGapsY : UnscaledGapsY {
      override val top: Int = 0
      override val bottom: Int = 0

      override fun copy(top: Int, bottom: Int): UnscaledGapsY {
        return UnscaledGapsYImpl(top, bottom)
      }

      override fun toString(): String {
        return "top = 0, bottom = 0"
      }
    }
  }

  val top: Int
  val bottom: Int

  val height: Int
    get() = top + bottom

  @ApiStatus.Experimental
  @ApiStatus.Internal
  fun copy(top: Int = this.top, bottom: Int = this.bottom): UnscaledGapsY
}

fun UnscaledGapsY(top: Int = 0, bottom: Int = 0): UnscaledGapsY {
  return UnscaledGapsYImpl(top, bottom)
}

private class UnscaledGapsYImpl(private val _top: Int, private val _bottom: Int) : UnscaledGapsY {

  override val top: Int
    get() = _top
  override val bottom: Int
    get() = _bottom

  init {
    checkNonNegative("top", top)
    checkNonNegative("bottom", bottom)
  }

  override fun copy(top: Int, bottom: Int): UnscaledGapsY {
    return UnscaledGapsYImpl(top, bottom)
  }

  override fun toString(): String {
    return "top = $top, bottom = $bottom"
  }
}

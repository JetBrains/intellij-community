// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative

/**
 * Defines left and right gaps. Values must be provided unscaled
 */
interface UnscaledGapsX {
  companion object {
    @JvmField
    val EMPTY: UnscaledGapsX = EmptyGapsX
  }

  val left: Int
  val right: Int

  val width: Int
    get() = left + right
}

fun UnscaledGapsX(left: Int = 0, right: Int = 0): UnscaledGapsX {
  return UnscaledGapsXImpl(left, right)
}

private object EmptyGapsX : UnscaledGapsX {
  override val left: Int = 0
  override val right: Int = 0

  override fun toString(): String {
    return "left = 0, right = 0"
  }
}

private class UnscaledGapsXImpl(private val _left: Int, private val _right: Int) : UnscaledGapsX {

  override val left: Int
    get() = _left
  override val right: Int
    get() = _right

  init {
    checkNonNegative("left", left)
    checkNonNegative("right", right)
  }

  override fun toString(): String {
    return "left = $left, right = $right"
  }
}

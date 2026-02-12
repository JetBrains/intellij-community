// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.design

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi

@Serializable
@ExperimentalIconsApi
class IconMargin(
  val top: IconUnit,
  val left: IconUnit,
  val bottom: IconUnit,
  val right: IconUnit,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IconMargin

    if (top != other.top) return false
    if (left != other.left) return false
    if (bottom != other.bottom) return false
    if (right != other.right) return false

    return true
  }

  override fun hashCode(): Int {
    var result = top.hashCode()
    result = 31 * result + left.hashCode()
    result = 31 * result + bottom.hashCode()
    result = 31 * result + right.hashCode()
    return result
  }

  override fun toString(): String {
    return "IconMargin(top=$top, left=$left, bottom=$bottom, right=$right)"
  }

  companion object {
    val Zero: IconMargin = IconMargin(0.dp, 0.dp, 0.dp, 0.dp)
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.utils

import java.awt.Rectangle

class ScreenInfo(val outerBounds: Rectangle?, val innerBounds: Rectangle?) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ScreenInfo

    if (outerBounds != other.outerBounds) return false
    if (innerBounds != other.innerBounds) return false

    return true
  }

  override fun hashCode(): Int {
    var result = outerBounds?.hashCode() ?: 0
    result = 31 * result + (innerBounds?.hashCode() ?: 0)
    return result
  }


}
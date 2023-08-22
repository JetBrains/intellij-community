// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import java.awt.Graphics2D
import java.awt.Point

internal fun Graphics2D.withTranslated(x: Int, y: Int, block: () -> Unit) {
  try {
    translate(x, y)
    block()
  } finally {
    translate(-x, -y)
  }
}

internal fun Graphics2D.withTranslated(x: Double, y: Double, block: () -> Unit) {
  try {
    translate(x, y)
    block()
  } finally {
    translate(-x, -y)
  }
}

internal fun Point.translateNew(dx: Int, dy: Int) : Point = Point(x + dx, y + dy)
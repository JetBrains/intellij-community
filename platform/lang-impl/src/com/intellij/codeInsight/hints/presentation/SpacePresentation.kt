// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics2D

class SpacePresentation(override val width: Int, override val height: Int) : BasePresentation() {
  override fun paint(g: Graphics2D, attributes: TextAttributes) {
  }

  override fun toString(): String = " "
}
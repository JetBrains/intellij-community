// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.Graphics
import java.util.concurrent.TimeUnit

internal class BlinkingLabel internal constructor(text: String) : JBLabel(text) {

  private var myFirstPainting = true

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    if (myFirstPainting) {
      myFirstPainting = false

      after(750) {
        reverseBold()
        after(750) { reverseBold() }
      }
    }
  }

  private fun reverseBold() {
    font = font.deriveFont(font.style xor Font.BOLD)
  }

  private fun after(delay: Long, f: () -> Unit) {
    AppExecutorUtil.getAppScheduledExecutorService().schedule({ UIUtil.invokeLaterIfNeeded(f) }, delay, TimeUnit.MILLISECONDS)
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.components.JBLabel
import java.awt.Font
import javax.swing.Timer
import javax.swing.event.AncestorEvent

internal class BlinkingLabel internal constructor(text: String) : JBLabel(text) {

  private val myTimer: Timer = Timer(750) { onTimer() }

  init {
    myTimer.isRepeats = false

    addAncestorListener(object: AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent?) {
        myTimer.start()
      }

      override fun ancestorRemoved(event: AncestorEvent?) {
        myTimer.stop()
      }
    })

    prepareSizeForBoldChange()
  }

  private fun onTimer() {
    if (isPlainFont()) {
      setBoldFont()
      myTimer.restart()
    } else {
      setPlainFont()
    }
  }

  private fun isPlainFont() = font.style and Font.BOLD == 0

  private fun setPlainFont() {
    font = font.deriveFont(font.style and Font.BOLD.inv())
  }

  private fun setBoldFont() {
    font = font.deriveFont(font.style or Font.BOLD)
  }

  private fun prepareSizeForBoldChange() {
    val sampleLabel = JBLabel(text)
    sampleLabel.font = font.deriveFont(font.style or Font.BOLD)
    minimumSize = sampleLabel.minimumSize
    preferredSize = sampleLabel.preferredSize
  }
}
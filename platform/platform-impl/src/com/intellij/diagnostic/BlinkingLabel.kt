// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.components.JBLabel
import java.awt.Font
import javax.swing.Timer
import javax.swing.event.AncestorEvent

internal class BlinkingLabel internal constructor(text: String) : JBLabel(text) {

  private var myPlainFont: Boolean = true
  private val myTimer: Timer = Timer(750) { onTimer() }

  init {
    myTimer.isRepeats = false

    addAncestorListener(object: AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent?) {
        this@BlinkingLabel.removeAncestorListener(this)

        myTimer.start()
      }
    })

    val sampleLabel = JBLabel(text)
    sampleLabel.font = font.deriveFont(font.style or Font.BOLD)
    minimumSize = sampleLabel.minimumSize
    preferredSize = sampleLabel.preferredSize
  }

  private fun onTimer() {
    if (myPlainFont) {
      setBoldFont()
      myTimer.restart()
    } else {
      setPlainFont()
    }
  }

  private fun setPlainFont() {
    myPlainFont = true
    font = font.deriveFont(font.style and Font.BOLD.inv())
  }

  private fun setBoldFont() {
    myPlainFont = false
    font = font.deriveFont(font.style or Font.BOLD)
  }
}
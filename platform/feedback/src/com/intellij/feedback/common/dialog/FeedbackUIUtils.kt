// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.ui.components.JBTextArea
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent


fun JBTextArea.adjustBehaviourForFeedbackForm() {
  wrapStyleWord = true
  lineWrap = true
  addKeyListener(object : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (e.keyCode == KeyEvent.VK_TAB) {
        if ((e.modifiersEx and KeyEvent.SHIFT_DOWN_MASK) != 0) {
          transferFocusBackward()
        }
        else {
          transferFocus()
        }
        e.consume()
      }
    }
  })
}

const val TEXT_AREA_ROW_SIZE = 5
const val TEXT_AREA_COLUMN_SIZE = 42
const val TEXT_FIELD_EMAIL_COLUMN_SIZE = 25
const val COMBOBOX_COLUMN_SIZE = 25

val EMAIL_REGEX = Regex(".+@.+\\..+")

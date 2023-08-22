// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import java.awt.Component
import javax.swing.text.JTextComponent

object DarculaTextFieldProperties {
  private val FORCE_TEXT_FIELD_ROUNDING: Key<Boolean> = Key.create("JTextField")

  /**
   * Allow forcing painting with rounded corners.
   */
  @JvmStatic
  fun makeTextFieldRounded(textFiled: JTextComponent) {
    ClientProperty.put(textFiled, FORCE_TEXT_FIELD_ROUNDING, true)
  }

  @JvmStatic
  fun isTextFieldRounded(textFiled: Component): Boolean {
    if (textFiled !is JTextComponent) return false

    return ClientProperty.isTrue(textFiled, FORCE_TEXT_FIELD_ROUNDING)
  }
}
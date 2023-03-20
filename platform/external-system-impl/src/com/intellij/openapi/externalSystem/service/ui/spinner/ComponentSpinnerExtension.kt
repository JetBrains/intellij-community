// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.spinner

import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import javax.swing.Icon
import javax.swing.JComponent

class ComponentSpinnerExtension private constructor() : ExtendableTextComponent.Extension {

  override fun getIcon(hovered: Boolean): Icon = AnimatedIcon.Default()

  companion object {
    private val SPINNER_EXTENSION = Key.create<ComponentSpinnerExtension>("ComponentSpinnerExtension")

    private fun <T> T.getOrPutSpinnerExtension(): ComponentSpinnerExtension where T : ExtendableTextComponent, T : JComponent {
      var extension = getUserData(SPINNER_EXTENSION)
      if (extension != null) {
        return extension
      }
      extension = ComponentSpinnerExtension()
      putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
      addExtension(extension)
      putUserData(SPINNER_EXTENSION, extension)
      return extension
    }

    fun <T> T.setSpinning(isSpinning: Boolean) where T : ExtendableTextComponent, T : JComponent {
      val extension = getOrPutSpinnerExtension()
      when {
        isSpinning -> addExtension(extension)
        else -> removeExtension(extension)
      }
      revalidate()
      repaint()
    }
  }
}
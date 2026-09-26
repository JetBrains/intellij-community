// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IslandsState
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Image

@Internal
interface DialogBackgroundImageProvider {
  companion object {
    @JvmStatic
    fun getInstance(): DialogBackgroundImageProvider = ApplicationManager.getApplication().service()
  }

  val isAvailable: Boolean get() = ExperimentalUI.isNewUI() && Registry.`is`("ide.onboarding.background.enabled", true)

  fun getImage(isDark: Boolean, isIslands: Boolean): Image?

  fun getImage(): Image? {
    val isDark = LafManager.getInstance().currentUIThemeLookAndFeel?.isDark ?: true
    val isIslands = IslandsState.isEnabled()
    return getImage(isDark, isIslands)
  }

  fun setBackgroundImageToDialog(dialog: DialogWrapper, image: Image?)
  fun hasBackgroundImage(dialog: DialogWrapper): Boolean
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.models.BundledLookAndFeel
import com.intellij.ide.ui.LafManager

object KnownLafs {
  val Light: BundledLookAndFeel
    get() = _light.value
  private val _light = lazy {
    LafManager.getInstance().defaultLightLaf?.let { BundledLookAndFeel(it) } ?: error("Light theme not found")
  }

  val Darcula: BundledLookAndFeel
    get() = _dark.value
  private val _dark = lazy {
    LafManager.getInstance().defaultDarkLaf?.let { BundledLookAndFeel(it) } ?: error("Dark theme not found")
  }

  val HighContrast: BundledLookAndFeel
    get() = _highContrast.value
  private val _highContrast = lazy {
    BundledLookAndFeel.fromManager("High contrast")
  }
}
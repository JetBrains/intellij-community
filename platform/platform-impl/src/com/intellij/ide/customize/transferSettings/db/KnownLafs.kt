// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.models.BundledLookAndFeel
import com.intellij.ide.ui.LafManager

object KnownLafs {
  val Light: BundledLookAndFeel = LafManager.getInstance().defaultLightLaf?.let { BundledLookAndFeel(it) } ?: error("Light theme not found")
  val Darcula: BundledLookAndFeel = LafManager.getInstance().defaultDarkLaf?.let { BundledLookAndFeel(it) } ?: error("Dark theme not found")
  val HighContrast: BundledLookAndFeel = BundledLookAndFeel.fromManager("High contrast")
}
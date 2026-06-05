// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.JComponent

@ApiStatus.Internal
interface NewUiOnboardingBrowserPageRenderer {
  fun createBrowserPageComponent(htmlText: String, size: Dimension): JComponent?

  companion object {
    private val EP_NAME = ExtensionPointName<NewUiOnboardingBrowserPageRenderer>("com.intellij.ide.newUiOnboarding.browserPageRenderer")

    fun createComponent(htmlText: String, size: Dimension): JComponent? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.createBrowserPageComponent(htmlText, size) }
  }
}

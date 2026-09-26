// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding.jcef

import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingBrowserPageRenderer
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent

@ApiStatus.Internal
class JcefNewUiOnboardingBrowserPageRenderer : NewUiOnboardingBrowserPageRenderer {
  override fun createBrowserPageComponent(htmlText: String, size: Dimension): JComponent? {
    if (!JBCefApp.isSupported()) {
      return null
    }

    val browser = JBCefBrowser.createBuilder().setMouseWheelEventEnable(false).build()
    browser.loadHTML(htmlText)
    return object : Wrapper(browser.component) {
      override fun paint(g: Graphics?) {
        super.paint(g)
        super.paintBorder(g)
      }
    }.also {
      UIUtil.setNotOpaqueRecursively(it)
      val adjustedSize = Dimension(size.width + 2, size.height + 2)
      it.minimumSize = adjustedSize
      it.preferredSize = adjustedSize
    }
  }
}

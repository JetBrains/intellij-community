// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenToolwindowFeatureFrontend

internal class IdeaTerminalFeatureFrontend : WelcomeScreenToolwindowFeatureFrontend() {
  override val featureKey: String = IdeaFeatureKeys.TERMINAL

  // matches TerminalToolWindowFactory.TOOL_WINDOW_ID; hardcoded to avoid a hard dependency on the Terminal plugin
  override val toolWindowId: String = "Terminal"
}

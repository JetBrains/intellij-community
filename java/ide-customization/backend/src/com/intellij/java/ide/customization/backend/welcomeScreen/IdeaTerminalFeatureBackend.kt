// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.customization.backend.welcomeScreen

import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenToolwindowFeatureBackend

internal class IdeaTerminalFeatureBackend : WelcomeScreenToolwindowFeatureBackend() {
  override val featureKey: String = "terminal.toolwindow"

  override val toolWindowId: String = "Terminal"
}
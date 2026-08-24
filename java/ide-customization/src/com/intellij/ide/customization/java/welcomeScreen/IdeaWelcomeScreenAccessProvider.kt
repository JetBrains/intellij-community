// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenAccessProvider

internal class IdeaWelcomeScreenAccessProvider: NonModalWelcomeScreenAccessProvider {
  override fun isAvailable() = Registry.`is`("idea.welcome.screen.non.modal.enabled", false)
}
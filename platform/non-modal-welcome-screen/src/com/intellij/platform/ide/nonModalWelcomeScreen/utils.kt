// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.options.advanced.AdvancedSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
const val NON_MODAL_WELCOME_SCREEN_SETTING_ID: String = "welcome.screen.non.modal.enabled"

internal val isNonModalWelcomeScreenEnabled: Boolean
  get() = AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID)

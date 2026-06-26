// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.ijent.LoginShellEnvVarMode
import com.intellij.platform.ijent.LoginShellEnvVarModeProvider

/**
 * `ServiceLoader`-discovered SPI forwarder for [LoginShellEnvVarModeProvider].
 *
 * The real implementation (e.g. `LoginShellEnvVarModeProviderImpl` in
 * `intellij.platform.ijent.community.ui`) is registered as an `applicationService` and looked up
 * through IntelliJ's service container, which crosses plugin-classloader boundaries unlike
 * `ServiceLoader`.
 *
 * Registered in `META-INF/services/com.intellij.platform.ijent.LoginShellEnvVarModeProvider`.
 */
internal class LoginShellEnvVarModeProviderSpiForwarder : LoginShellEnvVarModeProvider {
  override fun get(eelMachine: EelMachine): LoginShellEnvVarMode =
    delegate()?.get(eelMachine) ?: LoginShellEnvVarMode.LOGIN_INTERACTIVE

  private fun delegate(): LoginShellEnvVarModeProvider? {
    return serviceOrNull<LoginShellEnvVarModeProvider>().takeUnless { it === this }
  }
}

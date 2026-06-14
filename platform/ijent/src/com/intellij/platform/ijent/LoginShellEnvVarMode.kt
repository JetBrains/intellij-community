// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelMachine
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

/**
 * How the login shell should be invoked when collecting environment variables for an IJent process.
 */
@ApiStatus.Internal
enum class LoginShellEnvVarMode {
  /** Run the login shell interactively to collect its environment. */
  LOGIN_INTERACTIVE,

  /** Skip the interactive shell and use the IDE process's own environment. */
  LOGIN_NON_INTERACTIVE,
}

/**
 * SPI for resolving the current [LoginShellEnvVarMode]. The default impl in
 * `intellij.platform.ijent.community.ui` reads it from the ui setting.
 */
@ApiStatus.Internal
fun interface LoginShellEnvVarModeProvider {
  fun get(eelMachine: EelMachine): LoginShellEnvVarMode
}

private val provider: LoginShellEnvVarModeProvider? by lazy {
  ServiceLoader.load(LoginShellEnvVarModeProvider::class.java, LoginShellEnvVarModeProvider::class.java.classLoader).firstOrNull()
}

/**
 * Returns the current [LoginShellEnvVarMode], or [LoginShellEnvVarMode.LOGIN_INTERACTIVE] when no
 * provider is registered.
 */
@ApiStatus.Internal
fun loginShellEnvVarMode(eelMachine: EelMachine): LoginShellEnvVarMode =
  provider?.get(eelMachine) ?: LoginShellEnvVarMode.LOGIN_INTERACTIVE

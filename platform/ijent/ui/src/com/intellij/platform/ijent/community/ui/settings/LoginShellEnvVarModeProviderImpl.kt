// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.settings

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.ijent.LoginShellEnvVarMode
import com.intellij.platform.ijent.LoginShellEnvVarModeProvider
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle

internal class LoginShellEnvVarModeProviderImpl : LoginShellEnvVarModeProvider {
  /**
   * The advanced-setting-side enum, kept nested so its localized [toString] (which depends on
   * [IjentImplBundle], living in `intellij.platform.ijent.community.ui`) doesn't leak into the
   * public `ijent` module. The XML `<advancedSetting enumClass="…">` references this nested type
   * by its binary name.
   */
  @Suppress("unused") // Referenced by FQN from intellij.platform.ijent.community.ui.xml
  enum class EnvVarShellMode {
    LOGIN_INTERACTIVE {
      override fun toString(): String =
        IjentImplBundle.message("advanced.setting.container.environments.env.var.shell.mode.login.interactive")
    },
    LOGIN_NON_INTERACTIVE {
      override fun toString(): String =
        IjentImplBundle.message("advanced.setting.container.environments.env.var.shell.mode.login.non.interactive")
    },
  }

  override fun get(): LoginShellEnvVarMode =
    when (AdvancedSettings.getEnum("container.environments.env.var.shell.mode", EnvVarShellMode::class.java)) {
      EnvVarShellMode.LOGIN_INTERACTIVE -> LoginShellEnvVarMode.LOGIN_INTERACTIVE
      EnvVarShellMode.LOGIN_NON_INTERACTIVE -> LoginShellEnvVarMode.LOGIN_NON_INTERACTIVE
    }
}

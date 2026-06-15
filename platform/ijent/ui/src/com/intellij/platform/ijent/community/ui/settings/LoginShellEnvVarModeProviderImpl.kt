// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.settings

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.eel.EelMachine
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
    LOGIN_INTERACTIVE_SHELL {
      override fun toString(): String =
        IjentImplBundle.message("advanced.setting.container.environments.env.var.shell.mode.login.interactive.shell")
    },
  }

  override fun get(eelMachine: EelMachine): LoginShellEnvVarMode {
    return when (LoginShellEnvVarModeSettings.getInstance().get(eelMachine).envVarShellMode) {
      EnvVarShellMode.LOGIN_INTERACTIVE -> LoginShellEnvVarMode.LOGIN_INTERACTIVE
      EnvVarShellMode.LOGIN_NON_INTERACTIVE -> LoginShellEnvVarMode.LOGIN_NON_INTERACTIVE
      EnvVarShellMode.LOGIN_INTERACTIVE_SHELL -> LoginShellEnvVarMode.LOGIN_INTERACTIVE // TODO
    }
  }
}

@State(
  name = "LoginShellEnvVarModeSettings",
  storages = [Storage("nonLocalTargets.xml", roamingType = RoamingType.LOCAL)]
)
@Service(Service.Level.APP)
internal class LoginShellEnvVarModeSettings : PerMachineSettingsBase<LoginShellEnvVarModeSettings.TargetSettings, LoginShellEnvVarModeSettings.State>() {
  data class TargetSettings(
    var envVarShellMode: LoginShellEnvVarModeProviderImpl.EnvVarShellMode = LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_INTERACTIVE
  )
  class State : PerMachineState<TargetSettings> {
    override var data: MutableMap<String, TargetSettings> = mutableMapOf()
  }
  override var myState: State = State()
  override fun createDefault(machine: EelMachine): TargetSettings {
    return TargetSettings(envVarShellMode = AdvancedSettings.getEnum("container.environments.env.var.shell.mode", LoginShellEnvVarModeProviderImpl.EnvVarShellMode::class.java))
  }
  companion object {
    fun getInstance(): LoginShellEnvVarModeSettings = service()
  }
}

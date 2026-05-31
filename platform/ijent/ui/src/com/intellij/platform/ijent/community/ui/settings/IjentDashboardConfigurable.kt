// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.settings

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecPosixApi
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.platform.ijent.community.ui.actions.dashboard.EnvironmentVariablesDashboard
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.swing.JComponent

internal class IjentDashboardConfigurable(val project: Project) : SearchableConfigurable, Configurable.NoScroll {
  override fun getId(): String = "ijent.settings.dashboard"
  override fun getDisplayName(): String = IjentImplBundle.message("configurable.ijent.dashboard.display.name")

  val modeProperty = AtomicProperty(AdvancedSettings.getEnum("container.environments.env.var.shell.mode", LoginShellEnvVarModeProviderImpl.EnvVarShellMode::class.java))

  override fun createComponent(): JComponent {
    val eelDescriptor = project.getEelDescriptor()
    val envVarsTab: DialogPanel = panel {
      if (eelDescriptor.osFamily.isPosix) {
        row(IjentImplBundle.message("advanced.setting.container.environments.env.var.shell.mode")) {
          LoginShellEnvVarModeProviderImpl.EnvVarShellMode.entries
          val modeCombo = ComboBox(LoginShellEnvVarModeProviderImpl.EnvVarShellMode.entries.toTypedArray())
          cell(modeCombo).bindItem(modeProperty)
        }
      }
      val dashboard = EnvironmentVariablesDashboard(modeProperty.toFlow().map { choice ->
        @OptIn(EelDelicateApi::class)
        val mode = when (choice) {
          LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_INTERACTIVE -> EelExecApi.EnvironmentVariablesOptions.Mode.LOGIN_INTERACTIVE
          LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_NON_INTERACTIVE -> EelExecApi.EnvironmentVariablesOptions.Mode.LOGIN_NON_INTERACTIVE
        }
        EnvironmentVariablesDashboard.FetchEnvVarsMode {
          // TODO it's not what we have in fetchEnvironmentVariables
          val options = object : EelExecPosixApi.PosixEnvironmentVariablesOptions {
            override val mode = mode
            override val onlyActual = true
          }
          eelDescriptor.toEelApi().exec.environmentVariables(options).await()
        }
      })
      row { cell(dashboard.component).resizableColumn().align(Align.FILL) }.resizableRow()
    }
    return envVarsTab
  }

  override fun isModified(): Boolean {
    return modeProperty.get() != AdvancedSettings.getEnum("container.environments.env.var.shell.mode", LoginShellEnvVarModeProviderImpl.EnvVarShellMode::class.java)
  }
  override fun apply() {
    AdvancedSettings.setEnum("container.environments.env.var.shell.mode", modeProperty.get())
  }
}

private fun <T> ObservableProperty<T>.toFlow(): Flow<T> {
  val flow = MutableStateFlow(get())
  afterChange { flow.value = it }
  return flow
}
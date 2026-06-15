// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.settings

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getEelMachine
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnLoginShell
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.platform.ijent.community.ui.actions.dashboard.EnvironmentVariablesDashboard
import com.intellij.platform.ijent.community.ui.actions.dashboard.TerminalDashboard
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import com.jediterm.core.util.TermSize
import com.pty4j.PtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.swing.JComponent
import javax.swing.JLabel


internal class IjentDashboardConfigurable(val project: Project) : SearchableConfigurable, Configurable.NoScroll {
  override fun getId(): String = "ijent.settings.dashboard"
  override fun getDisplayName(): String = IjentImplBundle.message("configurable.ijent.dashboard.display.name")

  val eelMachine = project.getEelMachine()

  val modeProperty = AtomicProperty(LoginShellEnvVarModeSettings.getInstance().get(eelMachine).envVarShellMode)

  override fun createComponent(): JComponent {
    val eelDescriptor = project.getEelDescriptor()
    lateinit var terminalPlaceholder: Placeholder

    val root = panel {
      if (eelDescriptor.osFamily.isPosix) {
        row(IjentImplBundle.message("advanced.setting.container.environments.env.var.shell.mode")) {
          val modeCombo = ComboBox(LoginShellEnvVarModeProviderImpl.EnvVarShellMode.entries.toTypedArray())
          cell(modeCombo).bindItem(modeProperty)
        }
      }
      val envDashboard = EnvironmentVariablesDashboard(
        modeProperty.toFlow().map { _ ->
          envFetch(eelDescriptor.toEelApi(), modeProperty.get())
        }
      )
      row { cell(envDashboard.component).resizableColumn().align(Align.FILL) }.resizableRow()
      row {
        terminalPlaceholder = placeholder()
          .resizableColumn()
          .align(Align.FILL)
      }.resizableRow()

    }

    root.launchOnShow("ijent dashboard login-shell") {
      modeProperty.toFlow().collectLatest { choice ->
        coroutineScope {
          val sessionDisposable = Disposer.newDisposable("IjentDashboard terminal session")
          try {
            if (choice == LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_INTERACTIVE_SHELL) {
              withContext(Dispatchers.EDT) {
                terminalPlaceholder.component = JLabel(IdeBundle.message("progress.text.loading")).apply { border = JBUI.Borders.empty(8) }
              }
              val terminalDashboard = TerminalDashboard(project, sessionDisposable)
              val eel = eelDescriptor.toEelApi()
              val initialSize = TermSize(80, 24)
              val handle = eel.exec.spawnLoginShell()
                .pty(Pty(initialSize.columns, initialSize.rows, true))
                .scope(this)
                .workingDirectory(eel.userInfo.home)
                .eelIt()
              val ptyProcess = handle.process.convertToJavaProcess() as PtyProcess
              val widget = terminalDashboard.createWidget(ptyProcess, initialSize)
              withContext(Dispatchers.EDT) {
                terminalPlaceholder.component = widget.component
              }
            }
            else {
              withContext(Dispatchers.EDT) {
                terminalPlaceholder.component = null
              }
            }
            awaitCancellation()
          }
          catch (e: ExecuteProcessException) {
            logger<IjentDashboardConfigurable>().info(e)
            withContext(Dispatchers.EDT) {
              @Suppress("HardCodedStringLiteral")
              terminalPlaceholder.component = JLabel(e.toString())
            }
          }
          finally {
            Disposer.dispose(sessionDisposable)
          }
        }
      }
    }

    return root
  }

  @OptIn(EelDelicateApi::class)
  private fun envFetch(eel: EelApi, choice: LoginShellEnvVarModeProviderImpl.EnvVarShellMode): EnvironmentVariablesDashboard.FetchEnvVarsMode {
    val mode = when (choice) {
      LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_INTERACTIVE -> EelExecApi.EnvironmentVariablesOptions.Mode.LOGIN_INTERACTIVE
      LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_NON_INTERACTIVE -> EelExecApi.EnvironmentVariablesOptions.Mode.LOGIN_NON_INTERACTIVE
      LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_INTERACTIVE_SHELL -> EelExecApi.EnvironmentVariablesOptions.Mode.LOGIN_INTERACTIVE_VIA_SHELL
    }
    return EnvironmentVariablesDashboard.FetchEnvVarsMode {
      eel.exec.environmentVariables().onlyActual(true).mode(mode).eelIt().await()
    }
  }

  override fun isModified(): Boolean {
    return modeProperty.get() != LoginShellEnvVarModeSettings.getInstance().get(eelMachine).envVarShellMode
  }
  override fun apply() {
    LoginShellEnvVarModeSettings.getInstance().get(eelMachine).envVarShellMode = modeProperty.get()
  }
}

private fun <T> ObservableProperty<T>.toFlow(): Flow<T> {
  val flow = MutableStateFlow(get())
  afterChange { flow.value = it }
  return flow
}
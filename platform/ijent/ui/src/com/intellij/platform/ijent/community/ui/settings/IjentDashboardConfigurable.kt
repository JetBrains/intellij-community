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
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.LoginShellSpawner
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
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import com.jediterm.core.util.TermSize
import com.pty4j.PtyProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.time.Duration.Companion.seconds


internal class IjentDashboardConfigurable(val project: Project) : SearchableConfigurable, Configurable.NoScroll {
  override fun getId(): String = "ijent.settings.dashboard"
  override fun getDisplayName(): String = IjentImplBundle.message("configurable.ijent.dashboard.display.name")

  val eelMachine = project.getEelMachine()

  val modeProperty = AtomicProperty(LoginShellEnvVarModeSettings.getInstance().get(eelMachine).envVarShellMode)

  override fun createComponent(): JComponent {
    val eelDescriptor = project.getEelDescriptor()
    val terminalPlaceholder = com.intellij.ui.components.panels.Wrapper().apply {
      border = com.intellij.ui.IdeBorderFactory.createBorder(com.intellij.ui.SideBorder.ALL)
    }

    val envVarsFlow = MutableStateFlow<EnvironmentVariablesDashboard.FetchEnvVarsMode?>(null)

    val root = panel {
      if (eelDescriptor.osFamily.isPosix) {
        row(IjentImplBundle.message("advanced.setting.container.environments.env.var.shell.mode")) {
          val modeCombo = ComboBox(LoginShellEnvVarModeProviderImpl.EnvVarShellMode.entries.toTypedArray())
          cell(modeCombo).bindItem(modeProperty)
        }
      }
      val envDashboard = EnvironmentVariablesDashboard(envVarsFlow.filterNotNull())
      val splitter = com.intellij.ui.OnePixelSplitter(true, "IjentDashboardConfigurable.Terminal.Proportion", 0.75f).apply {
        firstComponent = envDashboard.component
        secondComponent = terminalPlaceholder
      }
      row { cell(splitter).resizableColumn().align(Align.FILL) }.resizableRow()

    }

    root.launchOnShow("ijent dashboard login-shell") {
      modeProperty.toFlow().collectLatest { choice ->
        coroutineScope {
          val envVarDeferred = CompletableDeferred<Map<String, String>>()
          envVarsFlow.value = EnvironmentVariablesDashboard.FetchEnvVarsMode {
            envVarDeferred.await()
          }
          val sessionDisposable = Disposer.newDisposable("IjentDashboard terminal session")
          var process: EelProcess? = null
          try {
            if (choice == LoginShellEnvVarModeProviderImpl.EnvVarShellMode.LOGIN_INTERACTIVE_SHELL) {
              withContext(Dispatchers.EDT) {
                terminalPlaceholder.setContent(JLabel(IdeBundle.message("progress.text.loading")).apply { border = JBUI.Borders.empty(8) })
              }
              val terminalDashboard = TerminalDashboard(project, sessionDisposable)
              val eelApi = eelDescriptor.toEelApi()
              val execApi = eelApi.exec
              if (execApi is LoginShellSpawner) {
                val initialSize = TermSize(80, 5)
                val handle = execApi.spawnLoginShell()
                  .pty(Pty(initialSize.columns, initialSize.rows, true))
                  .workingDirectory(eelApi.userInfo.home)
                  .eelIt()
                val ptyProcess = handle.process.convertToJavaProcess() as PtyProcess
                process = handle.process
                val widget = terminalDashboard.createWidget(ptyProcess, initialSize)
                withContext(Dispatchers.EDT) {
                  terminalPlaceholder.setContent(widget.component)
                }
                envVarDeferred.complete(handle.capturedEnv.await().associate { it.name to it.value })
              }
              else {
                withContext(Dispatchers.EDT) {
                  terminalPlaceholder.setContent(null)
                }
                envVarDeferred.complete(envFetch(eelDescriptor.toEelApi(), choice).doFetch())
              }
            }
            else {
              withContext(Dispatchers.EDT) {
                terminalPlaceholder.setContent(null)
              }
              envVarDeferred.complete(envFetch(eelDescriptor.toEelApi(), choice).doFetch())
            }
            awaitCancellation()
          }
          catch (e: ExecuteProcessException) {
            logger<IjentDashboardConfigurable>().info(e)
            withContext(Dispatchers.EDT) {
              @Suppress("HardCodedStringLiteral")
              terminalPlaceholder.setContent(JLabel(e.toString()))
            }
            envVarDeferred.completeExceptionally(e)
          }
          finally {
            withContext(NonCancellable) {
              process?.kill()
              withTimeoutOrNull(1.seconds) {
                process?.exitCode?.await()
              }
              Disposer.dispose(sessionDisposable)
            }
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
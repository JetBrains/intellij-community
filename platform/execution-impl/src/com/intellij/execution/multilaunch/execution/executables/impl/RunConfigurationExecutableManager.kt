// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.multilaunch.execution.executables.impl

import com.intellij.execution.*
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.RunConfigurationSelector
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.executables.ExecutableTemplate
import com.intellij.execution.multilaunch.state.ExecutableSnapshot
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.DataManager
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@Service(Service.Level.PROJECT)
class RunConfigurationExecutableManager(private val project: Project) : ExecutableTemplate {
  companion object {
    fun getInstance(project: Project) = project.service<RunConfigurationExecutableManager>()
  }

  override val type = "runConfig"
  override fun createExecutable(project: Project, configuration: MultiLaunchConfiguration, uniqueId: String): Executable? {
    return listExecutables(configuration).firstOrNull { it.uniqueId == uniqueId }
  }

  fun listExecutables(configuration: MultiLaunchConfiguration): List<Executable> {
    val configs = RunManager.getInstance(project).allSettings
    return configs
      .filter { it.configuration !is MultiLaunchConfiguration }
      .filter { it.configuration !is CompoundRunConfiguration }
      .map { RunConfigurationExecutable(configuration, project, it, this) }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  class RunConfigurationExecutable(
    private val configuration: MultiLaunchConfiguration,
    private val project: Project,
    val settings: RunnerAndConfigurationSettings,
    template: RunConfigurationExecutableManager
  ) : Executable(
    settings.uniqueID,
    settings.configuration.name,
    settings.configuration.icon,
    template
  ) {
    override val supportsDebugging = true
    override val supportsEditing = true

    override fun saveAttributes(snapshot: ExecutableSnapshot) {}
    override fun loadAttributes(snapshot: ExecutableSnapshot) {}

    override fun performEdit() {
      val dialog = EditConfigurationsDialog.findInstanceFromFocus() ?: return
      val dialogContext = DataManager.getInstance().getDataContext(dialog.getPreferredFocusedComponent())
      val selector = RunConfigurationSelector.KEY.getData(dialogContext) ?: return
      selector.select(settings.configuration)
    }

    override suspend fun execute(mode: ExecutionMode, activity: StructuredIdeActivity, lifetime: Lifetime): RunContentDescriptor? {
      val executor = when (mode) {
        ExecutionMode.Run -> DefaultRunExecutor.getRunExecutorInstance()
        ExecutionMode.Debug -> ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG)!!
      }

      val runContentDescriptor = suspendCancellableCoroutine { cont ->
        ExecutionUtil.doRunConfiguration(
          settings,
          executor,
          null,
          null,
          null)
        { executionEnvironment ->
          executionEnvironment.putUserData(ExecutionManagerImpl.PARENT_PROFILE_IDE_ACTIVITY, activity)
          val oldCallback = executionEnvironment.callback
          executionEnvironment.callback = object : ProgramRunner.Callback {
            override fun processStarted(runContentDescriptor: RunContentDescriptor?) {
              runContentDescriptor?.apply {
                isActivateToolWindowWhenAdded = configuration.parameters.activateToolWindows
                isAutoFocusContent = configuration.parameters.activateToolWindows
              }

              cont.resume(runContentDescriptor, null)
              oldCallback?.processStarted(runContentDescriptor)
            }
            override fun processNotStarted(error: Throwable?) {
              cont.cancel() // TODO: figure out if this is cancel or error or what
              oldCallback?.processNotStarted(error)
            }
          }
        }
      }

      lifetime.onTerminationIfAlive {
        ExecutionManagerImpl.stopProcess(runContentDescriptor)
      }

      when (runContentDescriptor) {
        null -> {
          val message = HtmlBuilder()
            .append(HtmlChunk
              .text(ExecutionBundle.message ("run.configurations.multilaunch.notification.title.incompatible.configuration.type", settings.configuration.type.displayName))
              .wrapWith("b"))
            .br()
            .append(ExecutionBundle.message("run.configurations.multilaunch.notification.description.incompatible.configuration.type"))
            .toString()

          ToolWindowManager.getInstance(project).notifyByBalloon(
            ToolWindowId.SERVICES,
            MessageType.WARNING,
            message
          )
        }
        else -> {
          suspendCancellableCoroutine { cont ->
            runContentDescriptor.processHandler?.addProcessListener(object : ProcessListener {
              override fun processTerminated(event: ProcessEvent) {
                cont.resume(Unit, null)
              }
              override fun processNotStarted() {
                cont.cancel()
              }
            })
          }
        }
      }

      return runContentDescriptor
    }

    private fun getBeforeRunTasks(configuration: RunConfiguration): List<BeforeRunTask<*>> {
      return when (configuration) {
        is WrappingRunConfiguration<*> -> getBeforeRunTasks(configuration.peer)
        else -> configuration.beforeRunTasks
      }
    }
  }
}
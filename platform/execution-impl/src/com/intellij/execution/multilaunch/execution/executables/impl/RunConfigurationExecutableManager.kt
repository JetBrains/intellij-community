package com.intellij.execution.multilaunch.execution.executables.impl

import com.intellij.execution.*
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.BeforeExecuteTask
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.executables.ExecutableTemplate
import com.intellij.execution.multilaunch.state.ExecutableSnapshot
import com.intellij.openapi.wm.ToolWindowId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

@Service(Service.Level.PROJECT)
class RunConfigurationExecutableManager(private val project: Project) : ExecutableTemplate {
  companion object {
    fun getInstance(project: Project) = project.service<RunConfigurationExecutableManager>()
  }

  override val type = "runConfig"
  override fun createExecutable(configuration: MultiLaunchConfiguration, uniqueId: String): Executable? {
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
  class RunConfigurationExecutable(private val configuration: MultiLaunchConfiguration, private val project: Project, val settings: RunnerAndConfigurationSettings, template: RunConfigurationExecutableManager) : Executable(
    settings.uniqueID,
    settings.configuration.name,
    settings.configuration.icon,
    template
  ) {
    override val beforeExecuteTasks: List<BeforeExecuteTask>
      get() = getBeforeRunTasks(settings.configuration)
        .map { BeforeRunTaskProvider.getProvider(project, it.providerId)?.name ?: it.providerId.toString()}
        .map { BeforeExecuteTask(it) }

    override val supportsDebugging: Boolean
      get() = true

    override fun saveAttributes(snapshot: ExecutableSnapshot) {}
    override fun loadAttributes(snapshot: ExecutableSnapshot) {}

    override suspend fun execute(mode: ExecutionMode, lifetime: Lifetime): RunContentDescriptor? {
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
          val oldCallback = executionEnvironment.callback
          executionEnvironment.callback = object : ProgramRunner.Callback {
            override fun processStarted(rcd: RunContentDescriptor) {
              rcd.isActivateToolWindowWhenAdded = configuration.parameters.activateToolWindows
              rcd.isAutoFocusContent = configuration.parameters.activateToolWindows
              //rcd.contentToolWindowId = "Services"
              cont.resume(rcd, null)
              oldCallback?.processStarted(rcd)
            }
            override fun processNotStarted() {
              cont.cancel() // TODO: figure out if this is cancel or error or what
              oldCallback?.processNotStarted()
            }
          }
        }
      }

      lifetime.onTerminationIfAlive {
        ExecutionManagerImpl.stopProcess(runContentDescriptor)
      }

      suspendCancellableCoroutine { cont ->
        runContentDescriptor.processHandler?.addProcessListener(object : ProcessAdapter() {
          override fun processTerminated(event: ProcessEvent) {
            cont.resume(Unit, null)
          }
          override fun processNotStarted() {
            cont.cancel()
          }
        })
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
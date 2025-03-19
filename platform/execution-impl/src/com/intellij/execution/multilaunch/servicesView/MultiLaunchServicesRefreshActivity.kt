package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.ExecutionEngine
import com.intellij.execution.multilaunch.execution.ExecutionModel
import com.intellij.execution.multilaunch.execution.MultiLaunchExecutionModel
import com.intellij.execution.multilaunch.execution.executables.impl.RunConfigurationExecutableManager
import com.intellij.execution.multilaunch.state.ExecutableRowSnapshotFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope

class MultiLaunchServicesRefreshActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val executionModel = ExecutionModel.getInstance(project)
    val runnerAndConfigurationsSettings = RunManager.getInstance(project).allSettings.filter { it.configuration is MultiLaunchConfiguration }
    ExecutionEngine.getInstance(project).initialize()
    executionModel.configurations.putAll(runnerAndConfigurationsSettings.associate {
      val configuration = it.configuration as MultiLaunchConfiguration
      configuration to MultiLaunchExecutionModel(it, configuration)
    })
    project.messageBus.connect(MyService.getInstance(project).scope).subscribe(RunManagerListener.TOPIC,MultiLaunchConfigurationsListener(project))
  }

  @Service(Service.Level.PROJECT)
  class MyService(val scope: CoroutineScope) {
    companion object {
      fun getInstance(project: Project): MyService = project.service()
    }
  }

  inner class MultiLaunchConfigurationsListener(private val project: Project) : RunManagerListener {
    private val serviceViewUpdatePublisher by lazy { MultiLaunchServicesRefresher.getInstance(project) }
    private val executionModel by lazy { ExecutionModel.getInstance(project) }
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
      when (val configuration = settings.configuration) {
        is MultiLaunchConfiguration -> {
          executionModel.configurations.putIfAbsent(configuration, MultiLaunchExecutionModel(settings, configuration))
          serviceViewUpdatePublisher.refresh()
        }
        !is CompoundRunConfiguration ->
          listContainingMultilaunchConfigs(settings).forEach(::refreshMultilaunch)
      }
    }

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
      val configuration = settings.configuration as? MultiLaunchConfiguration ?: return
      refreshMultilaunch(configuration)
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
      when (val configuration = settings.configuration) {
        is MultiLaunchConfiguration -> {
          executionModel.configurations.remove(configuration)
          serviceViewUpdatePublisher.refresh()
        }
        !is CompoundRunConfiguration -> {
          listContainingMultilaunchConfigs(settings).forEach(::refreshMultilaunch)
        }
      }
    }

    private fun listContainingMultilaunchConfigs(settings: RunnerAndConfigurationSettings): List<MultiLaunchConfiguration> {
      val runConfigType = RunConfigurationExecutableManager.getInstance(project).type
      val runConfigCompositeId = ExecutableRowSnapshotFactory.createCompositeId(runConfigType, settings.uniqueID)
      val multilaunchSnapshots = RunManager.getInstance(project).allSettings
        .map { it.configuration }
        .filterIsInstance<MultiLaunchConfiguration>()
        .map { it to it.state }

      return multilaunchSnapshots.filter { (_, snapshot) ->
        snapshot.rows.any { row -> row.executable?.id == runConfigCompositeId }
      }.map { it.first }
    }

    private fun refreshMultilaunch(configuration: MultiLaunchConfiguration) {
      val model = executionModel.configurations[configuration] ?: return
      executionModel.configurations.remove(configuration)
      executionModel.configurations[configuration] = model
      serviceViewUpdatePublisher.refresh(configuration)
    }
  }
}
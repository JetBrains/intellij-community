package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.ExecutionEngine
import com.intellij.execution.multilaunch.execution.ExecutionModel
import com.intellij.execution.multilaunch.execution.MultiLaunchExecutionModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope

class MultiLaunchServicesRefreshActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val executionModel = ExecutionModel.getInstance(project)
    val configurations = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<MultiLaunchConfiguration>()
    ExecutionEngine.getInstance(project).initialize()
    executionModel.configurations.putAll(configurations.associateWith { MultiLaunchExecutionModel(it) })
    project.messageBus.connect(MyService.getInstance(project).scope).subscribe(RunManagerListener.TOPIC,MultiLaunchConfigurationsListener(project))
  }

  @Service(Service.Level.PROJECT)
  class MyService(val scope: CoroutineScope) {
    companion object {
      fun getInstance(project: Project): MyService = project.service()
    }
  }

  inner class MultiLaunchConfigurationsListener(project: Project) : RunManagerListener {
    private val serviceViewUpdatePublisher by lazy { MultiLaunchServicesRefresher.getInstance(project) }
    private val executionModel by lazy { ExecutionModel.getInstance(project) }
    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
      val configuration = settings.configuration as? MultiLaunchConfiguration ?: return
      executionModel.configurations.putIfAbsent(configuration, MultiLaunchExecutionModel(configuration))
      serviceViewUpdatePublisher.refresh()
    }

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
      val configuration = settings.configuration as? MultiLaunchConfiguration ?: return
      val model = executionModel.configurations[configuration] ?: return
      executionModel.configurations.remove(configuration)
      executionModel.configurations[configuration] = model
      serviceViewUpdatePublisher.refresh(configuration)
    }

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
      val configuration = settings.configuration as? MultiLaunchConfiguration ?: return
      executionModel.configurations.remove(configuration)
      serviceViewUpdatePublisher.refresh()
    }
  }
}
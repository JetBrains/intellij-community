package com.intellij.execution.multilaunch.execution.executables

import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.ExecutableTemplate
import com.intellij.execution.multilaunch.execution.executables.TaskExecutableTemplate
import com.intellij.execution.multilaunch.execution.executables.impl.RunConfigurationExecutableManager
import com.intellij.execution.multilaunch.state.ExecutableSnapshot
import java.util.*

@Service(Service.Level.PROJECT)
class ExecutableFactory(private val project: Project) {
  companion object {
    fun getInstance(project: Project) = project.service<ExecutableFactory>()
    private fun generateUniqueId() = UUID.randomUUID().toString().take(6)
  }

  fun create(configuration: MultiLaunchConfiguration, snapshot: ExecutableSnapshot): Executable? {
    val templates = buildList {
      add(RunConfigurationExecutableManager.getInstance(project))
      addAll(TaskExecutableTemplate.EP_NAME.getExtensionList(project))
    }.associateBy { it.type }

    val (type, executableId) = snapshot.id?.split(":", limit = 2) ?: return null
    val template = templates[type] ?: return null
    return template.createExecutable(configuration, executableId)?.apply { loadAttributes(snapshot) }
  }

  fun create(configuration: MultiLaunchConfiguration, template: ExecutableTemplate) = template.createExecutable(configuration, generateUniqueId())
}


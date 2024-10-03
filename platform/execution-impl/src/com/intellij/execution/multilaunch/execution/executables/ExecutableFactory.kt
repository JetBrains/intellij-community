package com.intellij.execution.multilaunch.execution.executables

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.impl.RunConfigurationExecutableManager
import com.intellij.execution.multilaunch.state.ExecutableSnapshot
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ExecutableFactory(private val project: Project) {
  companion object {
    fun getInstance(project: Project) = project.service<ExecutableFactory>()
    private fun generateUniqueId() = UUID.randomUUID().toString().take(6)
  }

  fun create(configuration: MultiLaunchConfiguration, snapshot: ExecutableSnapshot): Executable? {
    val templates = buildList {
      add(RunConfigurationExecutableManager.getInstance(project))
      addAll(TaskExecutableTemplate.EP_NAME.extensionList)
    }.associateBy { it.type }

    val (type, executableId) = snapshot.id?.split(":", limit = 2) ?: return null
    val template = templates[type] ?: return null
    return template.createExecutable(project, configuration, executableId)?.apply { loadAttributes(snapshot) }
  }

  fun create(configuration: MultiLaunchConfiguration, template: ExecutableTemplate) = template.createExecutable(project, configuration, generateUniqueId())
}


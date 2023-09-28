package com.intellij.execution.multilaunch.execution.messaging

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable

@Service(Service.Level.PROJECT)
class ExecutionEventsBus(private val project: Project) {
  companion object {
    private val TOPIC = Topic.create("MultiLaunchExecutionNotifier.TOPIC", ExecutionNotifier::class.java)
    fun getInstance(project: Project) = project.service<ExecutionEventsBus>()
  }

  fun subscribe(configuration: MultiLaunchConfiguration, listener: ExecutionNotifier, lifetime: Lifetime): MessageBusConnection {
    return project.messageBus.connect(lifetime.createNestedDisposable()).apply { subscribe(TOPIC, ScopedExecutionNotifier(configuration, listener)) }
  }

  fun subscribe(listener: ExecutionNotifier, lifetime: Lifetime): MessageBusConnection {
    return project.messageBus.connect(lifetime.createNestedDisposable()).apply { subscribe(TOPIC, listener) }
  }

  fun createPublisher(): ExecutionNotifier {
    return project.messageBus.syncPublisher(TOPIC)
  }

  private class ScopedExecutionNotifier(
    private val configuration: MultiLaunchConfiguration,
    private val actualNotifier: ExecutionNotifier
  ) : ExecutionNotifier {
    override fun start(configuration: MultiLaunchConfiguration, executables: List<Executable>) {
      if (isApplicable(configuration)) actualNotifier.start(configuration, executables)
    }
    override fun cancel(configuration: MultiLaunchConfiguration) {
      if (isApplicable(configuration)) actualNotifier.cancel(configuration)
    }
    override fun finish(configuration: MultiLaunchConfiguration) {
      if (isApplicable(configuration)) actualNotifier.finish(configuration)
    }
    override fun beforeExecute(configuration: MultiLaunchConfiguration, executable: Executable) {
      if (isApplicable(configuration)) actualNotifier.beforeExecute(configuration, executable)
    }
    override fun execute(configuration: MultiLaunchConfiguration, executable: Executable) {
      if (isApplicable(configuration)) actualNotifier.execute(configuration, executable)
    }
    override fun afterExecute(configuration: MultiLaunchConfiguration, executable: Executable) {
      if (isApplicable(configuration)) actualNotifier.afterExecute(configuration, executable)
    }
    override fun afterSuccess(configuration: MultiLaunchConfiguration, executable: Executable) {
      if (isApplicable(configuration)) actualNotifier.afterSuccess(configuration, executable)
    }
    override fun afterCancel(configuration: MultiLaunchConfiguration, executable: Executable) {
      if (isApplicable(configuration)) actualNotifier.afterCancel(configuration, executable)
    }
    override fun afterFail(configuration: MultiLaunchConfiguration, executable: Executable, reason: Throwable?) {
      if (isApplicable(configuration)) actualNotifier.afterFail(configuration, executable, reason)
    }

    private fun isApplicable(eventConfiguration: MultiLaunchConfiguration) = eventConfiguration == configuration
  }
}
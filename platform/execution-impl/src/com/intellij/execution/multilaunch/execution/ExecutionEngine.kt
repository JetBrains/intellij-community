package com.intellij.execution.multilaunch.execution

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.multilaunch.execution.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.rd.util.concurrentMapOf
import com.jetbrains.rd.util.reactive.AddRemove
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.design.toRow
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.messaging.DefaultExecutionNotifier
import com.intellij.execution.multilaunch.execution.messaging.ExecutableNotifierProxy
import com.intellij.execution.multilaunch.execution.messaging.ExecutionEventsBus
import com.intellij.execution.multilaunch.execution.messaging.ExecutionNotifier
import com.intellij.execution.multilaunch.servicesView.MultiLaunchServicesRefresher
import com.intellij.openapi.rd.util.lifetime

@Service(Service.Level.PROJECT)
class ExecutionEngine(private val project: Project) {
  companion object {
    fun getInstance(project: Project) = project.service<ExecutionEngine>()
  }

  private val eventBus by lazy { ExecutionEventsBus.getInstance(project) }
  private val publisher by lazy { eventBus.createPublisher() }
  private val executionModel by lazy { ExecutionModel.getInstance(project) }
  private val serviceViewUpdatePublisher by lazy { MultiLaunchServicesRefresher.getInstance(project) }

  private var activeSessions = mutableMapOf<MultiLaunchConfiguration, ExecutionSession>()

  fun getActiveSession(configuration: MultiLaunchConfiguration): ExecutionSession? {
    return activeSessions[configuration]
  }

  private fun setActiveSession(configuration: MultiLaunchConfiguration, session: ExecutionSession?) {
    when (session) {
      null -> activeSessions.remove(configuration)
      else -> activeSessions.put(configuration, session)
    }
  }

  fun initialize() {
    eventBus.subscribe(StatusListener(), project.lifetime)
    executionModel.configurations.adviseAddRemove(project.lifetime) { event, configuration, configModel ->
      when (event) {
        AddRemove.Add -> {
          val descriptors = configuration.state.rows
            .mapNotNull { it.toRow(project, configuration).toDescriptor() }
            .let { proxifyDescriptors(configModel, publisher, it) }
          val pairs = descriptors.associate { it.executable to ExecutableExecutionModel(it) }
          configModel.executables.putAll(pairs)
          serviceViewUpdatePublisher.refresh(configuration)
        }
        AddRemove.Remove -> {
          configModel.executables.clear()
          serviceViewUpdatePublisher.refresh()
        }
      }
    }
  }

  suspend fun execute(configuration: MultiLaunchConfiguration, executionMode: ExecutionMode) {
    val configurationModel = executionModel.configurations[configuration] ?: throw CantRunException(
      ExecutionBundle.message("run.configurations.multilaunch.error.configuration.doesnt.exist"))

    getActiveSession(configuration)?.stop()

    val session = ExecutionSession(project, configurationModel)
    setActiveSession(configuration, session)

    markNotStarted(session, configurationModel)
    createConditionListeners(session, configurationModel, eventBus, executionMode)
    startMultiLaunch(session, publisher, configurationModel)

    session.awaitExecution()
  }

  fun stop(configuration: MultiLaunchConfiguration) {
    val session = getActiveSession(configuration) ?: return
    session.stop()
  }

  fun stop(configuration: MultiLaunchConfiguration, executable: Executable) {
    val session = getActiveSession(configuration) ?: return
    session.stop(executable)
  }

  private fun proxifyDescriptors(
    configurationModel: MultiLaunchExecutionModel,
    publisher: ExecutionNotifier,
    descriptors: List<ExecutionDescriptor>
  ): List<ExecutionDescriptor> {
    return descriptors
      .map {
        it.copy(executable = ExecutableNotifierProxy(configurationModel, it.executable, publisher))
      }
  }

  private fun markNotStarted(session: ExecutionSession, configurationModel: MultiLaunchExecutionModel) {
    configurationModel.executables.values.forEach {
      it.status.set(ExecutionStatus.NotStarted)
    }
    serviceViewUpdatePublisher.refresh(session.model.configuration)
  }

  private fun markWaiting(session: ExecutionSession, configurationModel: MultiLaunchExecutionModel) {
    configurationModel.executables.values.forEach {
      it.status.set(ExecutionStatus.Waiting)
    }
    serviceViewUpdatePublisher.refresh(session.model.configuration)
  }

  private fun createConditionListeners(
    session: ExecutionSession,
    configurationModel: MultiLaunchExecutionModel,
    eventBus: ExecutionEventsBus,
    executionMode: ExecutionMode
  ): List<MessageBusConnection> {
    return configurationModel.executables.values
      .mapNotNull {
        val mode = calculateExecutionMode(executionMode, it.descriptor)
        val lifetime = session.getLifetime(it.descriptor.executable) ?: return@mapNotNull null
        lifetime.onTerminationIfAlive {
          if (!it.status.value.isDone()) {
            setStatus(configurationModel.configuration, it.descriptor.executable, ExecutionStatus.Canceled)
          }
        }
        val notifier = it.descriptor.createListener(lifetime, mode)
        eventBus.subscribe(configurationModel.configuration, notifier, lifetime)
      }
  }

  private fun calculateExecutionMode(executionMode: ExecutionMode, descriptor: ExecutionDescriptor): ExecutionMode {
    return when {
      executionMode == ExecutionMode.Debug && descriptor.executable.supportsDebugging && !descriptor.disableDebugging -> ExecutionMode.Debug
      else -> ExecutionMode.Run
    }
  }

  private fun startMultiLaunch(session: ExecutionSession, publisher: ExecutionNotifier, configurationModel: MultiLaunchExecutionModel) {
    markWaiting(session, configurationModel)
    val executables = configurationModel.executables.keys.toList()
    project.lifetime.launchBackground {
      publisher.start(session.model.configuration, executables)
    }
  }

  private fun setStatus(configuration: MultiLaunchConfiguration, executable: Executable, status: ExecutionStatus) {
    executionModel.configurations[configuration]?.executables?.get(executable)?.status?.set(status)
    serviceViewUpdatePublisher.refresh(configuration)
  }

  inner class StatusListener : DefaultExecutionNotifier() {
    private val executablesLeft = concurrentMapOf<MultiLaunchConfiguration, HashSet<Executable>>()

    override fun start(configuration: MultiLaunchConfiguration, executables: List<Executable>) {
      executablesLeft[configuration] = executables.toHashSet()
    }

    override fun beforeExecute(configuration: MultiLaunchConfiguration, executable: Executable) {
      setStatus(configuration, executable, ExecutionStatus.Started)
    }

    override fun afterSuccess(configuration: MultiLaunchConfiguration, executable: Executable) {
      setStatus(configuration, executable, ExecutionStatus.Finished)
    }

    override fun afterCancel(configuration: MultiLaunchConfiguration, executable: Executable) {
      setStatus(configuration, executable, ExecutionStatus.Canceled)
    }

    override fun afterFail(configuration: MultiLaunchConfiguration, executable: Executable, reason: Throwable?) {
      setStatus(configuration, executable, ExecutionStatus.Failed(reason))
    }

    override fun afterExecute(configuration: MultiLaunchConfiguration, executable: Executable) {
      val session = getActiveSession(configuration) ?: return
      session.stop(executable)
      val left = executablesLeft[configuration] ?: return
      left.remove(executable)
      if (left.isEmpty()) {
        session.stop()
      }
    }
  }
}


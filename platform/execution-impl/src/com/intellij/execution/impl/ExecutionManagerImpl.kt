// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.CommonBundle
import com.intellij.build.BuildContentManager
import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.execution.*
import com.intellij.execution.configuration.CompatibilityAwareRunProfile
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfiguration.RestartSingletonResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ExecutionManagerImpl.Companion.DELEGATED_RUN_PROFILE_KEY
import com.intellij.execution.impl.ExecutionManagerImpl.Companion.TERMINATING_FOR_RERUN
import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector
import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector.RunConfigurationFinishType
import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector.UI_SHOWN_STAGE
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.getEffectiveTargetName
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.AppUIUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.Alarm
import com.intellij.util.SmartList
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.BorderLayout
import java.io.OutputStream
import java.lang.Runnable
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

open class ExecutionManagerImpl(private val project: Project, coroutineScope: CoroutineScope) : ExecutionManager(), Disposable {
  companion object {
    val LOG = logger<ExecutionManagerImpl>()
    private val EMPTY_PROCESS_HANDLERS = emptyArray<ProcessHandler>()

    internal val DELEGATED_RUN_PROFILE_KEY = Key.create<RunProfile>("DELEGATED_RUN_PROFILE_KEY")

    @Internal
    @JvmStatic
    fun getEnvironmentDataContext(): DataContext? {
      return currentThreadContext()[EnvDataContextElement]?.dataContext
    }

    @Internal
    fun withEnvironmentDataContext(dataContext: DataContext?): AccessToken {
      val context = currentThreadContext()
      return installThreadContext(context + EnvDataContextElement(dataContext), true)
    }

    private class EnvDataContextElement(val dataContext: DataContext?) : CoroutineContext.Element, IntelliJContextElement {
      companion object : CoroutineContext.Key<EnvDataContextElement>

      override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

      override val key: CoroutineContext.Key<*> = EnvDataContextElement
    }

    @JvmField
    val EXECUTION_SESSION_ID_KEY = ExecutionManager.EXECUTION_SESSION_ID_KEY

    @JvmField
    val EXECUTION_SKIP_RUN = ExecutionManager.EXECUTION_SKIP_RUN

    internal val TERMINATING_FOR_RERUN = Key.create<Boolean>("TERMINATING_FOR_RERUN")
    internal val REPORT_NEXT_START_AS_RERUN = Key.create<Boolean>("REPORT_NEXT_START_AS_RERUN")
    internal val PARENT_PROFILE_IDE_ACTIVITY = Key.create<StructuredIdeActivity>("PARENT_PROFILE_IDE_ACTIVITY")

    @JvmStatic
    fun getInstance(project: Project): ExecutionManagerImpl {
      return ExecutionManager.getInstance(project) as ExecutionManagerImpl
    }

    @JvmStatic
    fun getInstanceIfCreated(project: Project): ExecutionManagerImpl? {
      return project.serviceIfCreated<ExecutionManager>() as? ExecutionManagerImpl
    }

    @JvmStatic
    fun isProcessRunning(descriptor: RunContentDescriptor?): Boolean {
      val processHandler = descriptor?.processHandler
      return processHandler != null && !processHandler.isProcessTerminated
    }

    @JvmStatic
    fun stopProcess(descriptor: RunContentDescriptor?) {
      stopProcess(descriptor?.processHandler)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun stopProcess(processHandler: ProcessHandler?) {
      if (processHandler == null) {
        return
      }

      processHandler.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)
      GlobalScope.childScope("Destroy " + processHandler.javaClass.name, Dispatchers.Default, true).launch {
        if (processHandler is KillableProcess && processHandler.isProcessTerminating) {
          // process termination was requested, but it's still alive
          // in this case 'force quit' will be performed
          processHandler.killProcess()
        }
        else {
          if (!processHandler.isProcessTerminated) {
            if (processHandler.detachIsDefault()) {
              processHandler.detachProcess()
            }
            else {
              processHandler.destroyProcess()
            }
          }
        }
      }
    }

    @JvmStatic
    fun getAllDescriptors(project: Project): List<RunContentDescriptor> {
      return project.serviceIfCreated<RunContentManager>()?.allDescriptors ?: emptyList()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun setDelegatedRunProfile(runProfile: RunProfile, runProfileToDelegate: RunProfile) {
      if (runProfile !== runProfileToDelegate && runProfile is UserDataHolder) {
        DELEGATED_RUN_PROFILE_KEY[runProfile] = runProfileToDelegate
      }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getDelegatedRunProfile(runProfile: RunProfile): RunProfile? {
      return if (runProfile is UserDataHolder) runProfile.getUserData(DELEGATED_RUN_PROFILE_KEY) else null
    }
  }

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        if (project === this@ExecutionManagerImpl.project) {
          inProgress.clear()
        }
      }
    })
  }

  @set:TestOnly
  @Volatile
  var forceCompilationInTests = false

  private val awaitingTerminationAlarm = Alarm(coroutineScope, Alarm.ThreadToUse.SWING_THREAD)
  private val awaitingRunProfiles = HashMap<RunProfile, ExecutionEnvironment>()
  private val runningConfigurations: MutableList<RunningConfigurationEntry> = ContainerUtil.createLockFreeCopyOnWriteList()

  private val inProgress = Collections.synchronizedSet(HashSet<InProgressEntry>())

  private fun processNotStarted(environment: ExecutionEnvironment, activity: StructuredIdeActivity?, e : Throwable? = null) {
    RunConfigurationUsageTriggerCollector.logProcessFinished(activity, RunConfigurationFinishType.FAILED_TO_START)
    val executorId = environment.executor.id
    val inProgressEntry = InProgressEntry(
      environment.runnerAndConfigurationSettings?.uniqueID ?: "",
      executorId, environment.runner.runnerId)
    inProgress.remove(inProgressEntry)
    environment.callback?.processNotStarted(e)
    project.messageBus.syncPublisher(EXECUTION_TOPIC).processNotStarted(executorId, environment, e)
  }

  /**
   * Internal usage only. Maybe removed or changed in any moment. No backward compatibility.
   */
  @ApiStatus.Internal
  override fun startRunProfile(environment: ExecutionEnvironment, starter: () -> Promise<RunContentDescriptor?>) {
    doStartRunProfile(environment) {
      // errors are handled by startRunProfile
      starter()
        .then { descriptor ->
          if (descriptor != null) {
            descriptor.executionId = environment.executionId

            val toolWindowId = RunContentManager.getInstance(environment.project).getContentDescriptorToolWindowId(environment)
            if (toolWindowId != null) {
              descriptor.contentToolWindowId = toolWindowId
            }

            environment.runnerAndConfigurationSettings?.let {
              descriptor.isActivateToolWindowWhenAdded = it.isActivateToolWindowBeforeRun || it.isFocusToolWindowBeforeRun
              descriptor.isAutoFocusContent= it.isFocusToolWindowBeforeRun
            }
          }
          environment.callback?.let {
            it.processStarted(descriptor)
            environment.callback = null
          }
          descriptor
        }
    }
  }

  override fun startRunProfile(starter: RunProfileStarter, environment: ExecutionEnvironment) {
    doStartRunProfile(environment) {
      starter.executeAsync(environment)
    }
  }

  private fun doStartRunProfile(environment: ExecutionEnvironment, task: () -> Promise<RunContentDescriptor>) {
    val activity = triggerUsage(environment)

    RunManager.getInstance(environment.project).refreshUsagesList(environment.runProfile)

    val project = environment.project
    val reuseContent = RunContentManager.getInstance(project).getReuseContent(environment)
    if (reuseContent != null) {
      reuseContent.executionId = environment.executionId
      environment.contentToReuse = reuseContent
    }

    val executor = environment.executor
    val inProgressEntry = InProgressEntry(
      environment.runnerAndConfigurationSettings?.uniqueID ?: "",
      executor.id, environment.runner.runnerId)
    inProgress.add(inProgressEntry)
    project.messageBus.syncPublisher(EXECUTION_TOPIC).processStartScheduled(executor.id, environment)
    registerRecentExecutor(environment)

    val startRunnable = Runnable {
      if (project.isDisposed) {
        return@Runnable
      }

      project.messageBus.syncPublisher(EXECUTION_TOPIC).processStarting(executor.id, environment)

      fun handleError(e: Throwable) {
        processNotStarted(environment, activity, e)
        if (e !is ProcessCanceledException) {
          handleProgramRunnerExecutionError(project, environment, e, environment.runProfile)
          LOG.debug(e)
        }
      }

      try {
        task()
          .onSuccess { descriptor ->
            AppUIUtil.invokeLaterIfProjectAlive(project) {
              if (descriptor == null) {
                processNotStarted(environment, activity)
                return@invokeLaterIfProjectAlive
              }

              val entry = RunningConfigurationEntry(descriptor, environment, executor)
              runningConfigurations.add(entry)
              Disposer.register(descriptor, Disposable { runningConfigurations.remove(entry) })
              if (!descriptor.isHiddenContent && !environment.isHeadless) {
                RunContentManager.getInstance(project).showRunContent(executor, descriptor, environment.contentToReuse)
              }
              activity?.stageStarted(UI_SHOWN_STAGE)
              environment.contentToReuse = descriptor

              val processHandler = descriptor.processHandler
              if (processHandler != null) {
                if (!processHandler.isStartNotified) {
                  project.messageBus.syncPublisher(EXECUTION_TOPIC).processStarting(executor.id, environment, processHandler)
                  processHandler.startNotify()
                }
                inProgress.remove(inProgressEntry)
                project.messageBus.syncPublisher(EXECUTION_TOPIC).processStarted(executor.id, environment, processHandler)

                val listener = ProcessExecutionListener(project, executor.id, environment, descriptor, activity)
                processHandler.addProcessListener(listener)

                // Since we cannot guarantee that the listener is added before process handled is start notified,
                // we have to make sure the process termination events are delivered to the clients.
                // Here we check the current process state and manually deliver events, while
                // the ProcessExecutionListener guarantees each such event is only delivered once
                // either by this code, or by the ProcessHandler.
                val terminating = processHandler.isProcessTerminating
                val terminated = processHandler.isProcessTerminated
                if (terminating || terminated) {
                  listener.processWillTerminate(ProcessEvent(processHandler), false /* doesn't matter */)
                  if (terminated) {
                    val exitCode = if (processHandler.isStartNotified) processHandler.exitCode ?: -1 else -1
                    listener.processTerminated(ProcessEvent(processHandler, exitCode))
                  }
                }
              }
            }
          }
          .onError(::handleError)
      }
      catch (e: Throwable) {
        handleError(e)
      }
    }

    if (!forceCompilationInTests && ApplicationManager.getApplication().isUnitTestMode) {
      startRunnable.run()
    }
    else {
      compileAndRun(Runnable {
        ApplicationManager.getApplication().invokeLater(startRunnable, project.disposed)
      }, environment, Runnable {
        if (!project.isDisposed) {
          processNotStarted(environment, activity)
        }
      })
    }
  }

  private fun registerRecentExecutor(environment: ExecutionEnvironment) {
    environment.runnerAndConfigurationSettings?.let {
      PropertiesComponent.getInstance(project).setValue(it.uniqueID + ".executor", environment.executor.id)
    }
  }

  fun getRecentExecutor(setting: RunnerAndConfigurationSettings): Executor? {
    val executorId = PropertiesComponent.getInstance(project).getValue(setting.uniqueID + ".executor")
    return executorId?.let { ExecutorRegistry.getInstance().getExecutorById(it) }
  }

  override fun dispose() {
    for (entry in runningConfigurations) {
      Disposer.dispose(entry.descriptor)
    }
    runningConfigurations.clear()
  }

  @Suppress("OverridingDeprecatedMember")
  override fun getContentManager() = RunContentManager.getInstance(project)

  override fun getRunningProcesses(): Array<ProcessHandler> {
    var handlers: MutableList<ProcessHandler>? = null
    for (descriptor in getAllDescriptors(project)) {
      val processHandler = descriptor.processHandler ?: continue
      if (handlers == null) {
        handlers = SmartList()
      }
      handlers.add(processHandler)
    }
    return handlers?.toTypedArray() ?: EMPTY_PROCESS_HANDLERS
  }

  override fun compileAndRun(startRunnable: Runnable, environment: ExecutionEnvironment, onCancelRunnable: Runnable?) {
    var id = environment.executionId
    if (id == 0L) {
      id = environment.assignNewExecutionId()
    }

    val profile = environment.runProfile
    if (profile !is RunConfiguration) {
      startRunnable.run()
      return
    }

    val beforeRunTasks = doGetBeforeRunTasks(profile)
    if (beforeRunTasks.isEmpty()) {
      startRunnable.run()
      return
    }

    val context = environment.dataContext
    val projectContext = context ?: SimpleDataContext.getProjectContext(project)
    val runBeforeRunExecutorMap = Collections.synchronizedMap(linkedMapOf<BeforeRunTask<*>, Executor>())

    ApplicationManager.getApplication().executeOnPooledThread {
      for (task in beforeRunTasks) {
        val provider = BeforeRunTaskProvider.getProvider(project, task.providerId)
        if (provider == null || task !is RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask) {
          continue
        }

        val settings = task.getSettings()
        if (settings != null) {
          // as side-effect here we setup runners list ( required for com.intellij.execution.impl.RunManagerImpl.canRunConfiguration() )
          var executor = if (Registry.`is`("lock.run.executor.for.before.run.tasks", false)) {
            DefaultRunExecutor.getRunExecutorInstance()
          }
          else {
            environment.executor
          }

          val builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings)
          if (builder == null || !RunManagerImpl.canRunConfiguration(settings, executor)) {
            executor = DefaultRunExecutor.getRunExecutorInstance()
            if (!RunManagerImpl.canRunConfiguration(settings, executor)) {
              // we should stop here as before run task cannot be executed at all (possibly it's invalid)
              onCancelRunnable?.run()
              handleExecutionError(environment, ExecutionException(
                ExecutionBundle.message("dialog.message.cannot.start.before.run.task", settings)))
              return@executeOnPooledThread
            }
          }
          runBeforeRunExecutorMap[task] = executor
        }
      }

      for (task in beforeRunTasks) {
        if (project.isDisposed) {
          return@executeOnPooledThread
        }

        @Suppress("UNCHECKED_CAST")
        val provider = BeforeRunTaskProvider.getProvider(project, task.providerId) as BeforeRunTaskProvider<BeforeRunTask<*>>?
        if (provider == null) {
          LOG.warn("Cannot find BeforeRunTaskProvider for id='${task.providerId}'")
          continue
        }

        val builder = ExecutionEnvironmentBuilder(environment).contentToReuse(null)
        val executor = runBeforeRunExecutorMap[task]
        if (executor != null) {
          builder.executor(executor)
        }

        val taskEnvironment = builder.build()
        taskEnvironment.executionId = id
        EXECUTION_SESSION_ID_KEY.set(taskEnvironment, id)
        try {
          if (!provider.executeTask(projectContext, profile, taskEnvironment, task)) {
            LOG.debug("Before launch task '$task' doesn't finish successfully, cancelling execution")
            if (onCancelRunnable != null) {
              SwingUtilities.invokeLater(onCancelRunnable)
            }
            return@executeOnPooledThread
          }
        }
        catch (e: ProcessCanceledException) {
          LOG.debug("Before launch task '$task' cancelled, cancelling execution", e)
          if (onCancelRunnable != null) {
            SwingUtilities.invokeLater(onCancelRunnable)
          }
          return@executeOnPooledThread
        }
      }

      doRun(environment, startRunnable)
    }
  }

  private fun doRun(environment: ExecutionEnvironment, startRunnable: Runnable) {
    val allowSkipRun = environment.getUserData(EXECUTION_SKIP_RUN)
    if (allowSkipRun != null && allowSkipRun) {
      processNotStarted(environment, null)
      return
    }

    runInEdt(ModalityState.any()) {
      if (project.isDisposed) {
        return@runInEdt
      }

      val settings = environment.runnerAndConfigurationSettings
      if (settings != null && !settings.type.isDumbAware && DumbService.isDumb(project)) {
        DumbService.getInstance(project).runWhenSmart(startRunnable)
      }
      else {
        try {
          startRunnable.run()
        }
        catch (ignored: IndexNotReadyException) {
          handleExecutionError(environment, ExecutionException(
            ExecutionBundle.message("dialog.message.cannot.start.while.indexing.in.progress")))
        }
      }
    }
  }

  override fun restartRunProfile(project: Project,
                                 executor: Executor,
                                 target: ExecutionTarget,
                                 configuration: RunnerAndConfigurationSettings?,
                                 processHandler: ProcessHandler?,
                                 environmentCustomization: Consumer<in ExecutionEnvironment>?) {
    val builder = createEnvironmentBuilder(project, executor, configuration)
    if (processHandler != null) {
      for (descriptor in getAllDescriptors(project)) {
        if (descriptor.processHandler === processHandler) {
          builder.contentToReuse(descriptor)
          break
        }
      }
    }
    val environment = builder.target(target).build()
    environmentCustomization?.accept(environment)
    restartRunProfile(environment)
  }

  override fun restartRunProfile(environment: ExecutionEnvironment) {
    val configuration = environment.runnerAndConfigurationSettings

    val runningIncompatible: List<RunContentDescriptor>
    if (configuration == null) {
      runningIncompatible = emptyList()
    }
    else {
      runningIncompatible = getIncompatibleRunningDescriptors(configuration)
    }

    val contentToReuse = environment.contentToReuse
    val runningOfTheSameType = if (configuration != null && !configuration.configuration.isAllowRunningInParallel) {
      getRunningDescriptors(Condition { it.isOfSameType(configuration) })
    }
    else if (isProcessRunning(contentToReuse)) {
      listOf(contentToReuse!!)
    }
    else {
      emptyList()
    }

    val runningToStop = ContainerUtil.concat(runningOfTheSameType, runningIncompatible)
    if (runningToStop.isNotEmpty()) {
      if (configuration != null) {
        if (runningOfTheSameType.isNotEmpty() && (runningOfTheSameType.size > 1 || contentToReuse == null || runningOfTheSameType.first() !== contentToReuse)) {
          val result = configuration.configuration.restartSingleton(environment)
          if (result == RestartSingletonResult.NO_FURTHER_ACTION) {
            return
          }
          if (result == RestartSingletonResult.ASK_AND_RESTART && !userApprovesStopForSameTypeConfigurations(environment.project, configuration.name, runningOfTheSameType.size)) {
            return
          }
        }
        if (runningIncompatible.isNotEmpty() && !userApprovesStopForIncompatibleConfigurations(project, configuration.name, runningIncompatible)) {
          return
        }
      }

      for (descriptor in runningToStop) {
        descriptor?.processHandler?.putUserData(TERMINATING_FOR_RERUN, true)
        stopProcess(descriptor)
      }

      // This ExecutionEnvironment might be a fresh new object, so, technically, it hasn't been started yet,
      // but it is going to be started because of the 'Rerun' action, so, need to report to FUS as rerun.
      environment.putUserData(REPORT_NEXT_START_AS_RERUN, true)
    }

    if (awaitingRunProfiles[environment.runProfile] === environment) {
      // defense from rerunning exactly the same ExecutionEnvironment
      return
    }

    awaitingRunProfiles[environment.runProfile] = environment

    // Can be called via Alarm, which doesn't provide implicit WIRA
    awaitTermination(object : Runnable {
      override fun run() {
        WriteIntentReadAction.run wira@{
          if (awaitingRunProfiles[environment.runProfile] !== environment) {
            // a new rerun has been requested before starting this one, ignore this rerun
            return@wira
          }
          val inProgressEntry = InProgressEntry(configuration?.uniqueID ?: "", environment.executor.id, environment.runner.runnerId)
          if ((configuration != null && !configuration.type.isDumbAware && DumbService.getInstance(project).isDumb)
              || inProgress.contains(inProgressEntry)) {
            awaitTermination(this, 100)
            return@wira
          }

          for (descriptor in runningOfTheSameType) {
            val processHandler = descriptor.processHandler
            if (processHandler != null && !processHandler.isProcessTerminated) {
              awaitTermination(this, 100)
              return@wira
            }
          }

          awaitingRunProfiles.remove(environment.runProfile)

          // start() can be called during restartRunProfile() after pretty long 'awaitTermination()' so we have to check if the project is still here
          if (environment.project.isDisposed) {
            return@wira
          }

          val settings = environment.runnerAndConfigurationSettings
          executeConfiguration(environment, settings != null && settings.isEditBeforeRun)
        }
      }
    }, 50)
  }

  private class MyProcessHandler : ProcessHandler() {
    override fun destroyProcessImpl() {}
    override fun detachProcessImpl() {}
    override fun detachIsDefault(): Boolean {
      return false
    }

    override fun getProcessInput(): OutputStream? = null

    public override fun notifyProcessTerminated(exitCode: Int) {
      super.notifyProcessTerminated(exitCode)
    }
  }

  override fun executePreparationTasks(environment: ExecutionEnvironment, currentState: RunProfileState): Promise<Any?> {
    if (!(environment.runProfile is TargetEnvironmentAwareRunProfile)) {
      return resolvedPromise()
    }

    val targetEnvironmentAwareRunProfile = environment.runProfile as TargetEnvironmentAwareRunProfile
    if (!targetEnvironmentAwareRunProfile.needPrepareTarget()) {
      return resolvedPromise()
    }

    val processHandler = MyProcessHandler()
    val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project).console
    ProcessTerminatedListener.attach(processHandler)
    consoleView.attachToProcess(processHandler)

    val component = TargetPrepareComponent(consoleView)
    val buildContentManager = BuildContentManager.getInstance(environment.project)
    val contentName = targetEnvironmentAwareRunProfile.getEffectiveTargetName(environment.project)?.let {
      ExecutionBundle.message("tab.title.prepare.environment", it, environment.runProfile.name)
    } ?: ExecutionBundle.message("tab.title.prepare.target.environment", environment.runProfile.name)
    val toolWindow = buildContentManager.orCreateToolWindow
    val contentManager: ContentManager = toolWindow.contentManager
    val contentImpl = ContentImpl(component, contentName, true)
    contentImpl.putUserData(ToolWindow.SHOW_CONTENT_ICON, java.lang.Boolean.TRUE)
    contentImpl.icon = environment.runProfile.icon

    for (content in contentManager.contents) {
      if (contentName != content.displayName) continue
      if (content.isPinned) continue

      val contentComponent = content.component
      if (contentComponent !is TargetPrepareComponent) continue


      if (contentComponent.isPreparationFinished()) {
        contentManager.removeContent(content, true)
      }
    }

    contentManager.addContent(contentImpl)
    contentManager.setSelectedContent(contentImpl)
    toolWindow.activate(null)

    val promise = AsyncPromise<Any?>()
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        processHandler.startNotify()
        val targetProgressIndicator = object : TargetProgressIndicator {
          @Volatile
          var stopped = false

          override fun addText(text: @Nls String, key: Key<*>) {
            processHandler.notifyTextAvailable(text, key)
          }

          override fun isCanceled(): Boolean {
            return false
          }

          override fun stop() {
            stopped = true
          }

          override fun isStopped(): Boolean = stopped
        }
        promise.setResult(environment.prepareTargetEnvironment(currentState, targetProgressIndicator))
      }
      catch (t: Throwable) {
        LOG.warn(t)
        promise.setError(ExecutionBundle.message("message.error.happened.0", t.localizedMessage))
        processHandler.notifyTextAvailable(StringUtil.notNullize(t.localizedMessage), ProcessOutputType.STDERR)
        processHandler.notifyTextAvailable("\n", ProcessOutputType.STDERR)
      }
      finally {
        val exitCode = if (promise.isSucceeded) 0 else -1
        processHandler.notifyProcessTerminated(exitCode)
        component.setPreparationFinished()
      }
    }
    return promise
  }

  @ApiStatus.Internal
  fun executeConfiguration(environment: ExecutionEnvironment, showSettings: Boolean, assignNewId: Boolean = true) {
    withEnvironmentDataContext(environment.dataContext).use {
      val runnerAndConfigurationSettings = environment.runnerAndConfigurationSettings
      val project = environment.project
      val runner = environment.runner
      if (runnerAndConfigurationSettings != null) {
        val targetManager = ExecutionTargetManager.getInstance(project)
        if (!targetManager.doCanRun(runnerAndConfigurationSettings.configuration, environment.executionTarget)) {
          handleExecutionError(environment, ExecutionException(
            ProgramRunnerUtil.getCannotRunOnErrorMessage(environment.runProfile, environment.executionTarget)))
          processNotStarted(environment, null)
          return
        }

        if (!DumbService.isDumb(project)) {
          if (showSettings && runnerAndConfigurationSettings.isEditBeforeRun) {
            if (!RunDialog.editConfiguration(environment, ExecutionBundle.message("dialog.title.edit.configuration", 0))) {
              processNotStarted(environment, null)
              return
            }
            editConfigurationUntilSuccess(environment, assignNewId)
          }
          else {
            val inProgressEntry = InProgressEntry(
              environment.runnerAndConfigurationSettings?.uniqueID  ?: "",
              environment.executor.id, environment.runner.runnerId)
            inProgress.add(inProgressEntry)
            ReadAction.nonBlocking(Callable { RunManagerImpl.canRunConfiguration(environment) })
              .finishOnUiThread(ModalityState.nonModal()) { canRun ->
                inProgress.remove(inProgressEntry)
                if (canRun) {

                  executeConfiguration(environment, environment.runner, assignNewId, this.project,
                                       environment.runnerAndConfigurationSettings)

                  return@finishOnUiThread
                }

                if (!RunDialog.editConfiguration(environment, ExecutionBundle.message("dialog.title.edit.configuration", 0))) {
                  processNotStarted(environment, null)
                  return@finishOnUiThread
                }
                editConfigurationUntilSuccess(environment, assignNewId)
              }
              .expireWith(this)
              .submit(AppExecutorUtil.getAppExecutorService())
          }
          return
        }
      }

      executeConfiguration(environment, runner, assignNewId, project, runnerAndConfigurationSettings)
    }
  }

  private fun editConfigurationUntilSuccess(environment: ExecutionEnvironment, assignNewId: Boolean) {
    ReadAction.nonBlocking(Callable { RunManagerImpl.canRunConfiguration(environment) })
      .finishOnUiThread(ModalityState.nonModal()) { canRun ->
        val runAnyway = if (!canRun) {
          val message = ExecutionBundle.message("dialog.message.configuration.still.incorrect.do.you.want.to.edit.it.again")
          val title = ExecutionBundle.message("dialog.title.change.configuration.settings")
          Messages.showYesNoDialog(project, message, title, CommonBundle.message("button.edit"), ExecutionBundle.message("run.continue.anyway"), Messages.getErrorIcon()) != Messages.YES
        } else true
        if (canRun || runAnyway) {
          val runner = ProgramRunner.getRunner(environment.executor.id, environment.runnerAndConfigurationSettings!!.configuration)
          if (runner == null) {
            handleExecutionError(environment,
                                               ExecutionException(ExecutionBundle.message("dialog.message.cannot.find.runner",
                                                                                          environment.runProfile.name)))
          }
          else {
            executeConfiguration(environment, runner, assignNewId, project, environment.runnerAndConfigurationSettings)
          }
          return@finishOnUiThread
        }
        if (!RunDialog.editConfiguration(environment, ExecutionBundle.message("dialog.title.edit.configuration", 0))) {
          processNotStarted(environment, null)
          return@finishOnUiThread
        }

        editConfigurationUntilSuccess(environment, assignNewId)
      }
      .expireWith(this)
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  protected open fun executeConfiguration(environment: ExecutionEnvironment,
                                          runner: @NotNull ProgramRunner<*>,
                                          assignNewId: Boolean,
                                          project: @NotNull Project,
                                          runnerAndConfigurationSettings: @Nullable RunnerAndConfigurationSettings?) {
    try {
      var effectiveEnvironment = environment
      if (runner != effectiveEnvironment.runner) {
        effectiveEnvironment = ExecutionEnvironmentBuilder(effectiveEnvironment).runner(runner).build()
      }
      if (assignNewId) {
        effectiveEnvironment.assignNewExecutionId()
      }
      runner.execute(effectiveEnvironment)
    }
    catch (e: ExecutionException) {
      handleProgramRunnerExecutionError(project, environment, e, runnerAndConfigurationSettings?.configuration)
    }
  }

  @ApiStatus.Internal
  protected open fun handleProgramRunnerExecutionError(project: Project, environment: ExecutionEnvironment, e: Throwable, profile: RunProfile?) {
    ProgramRunnerUtil.handleExecutionError(project, environment, e, profile)
  }

  @ApiStatus.Internal
  protected open fun handleExecutionError(environment: ExecutionEnvironment, e: ExecutionException) {
    ExecutionUtil.handleExecutionError(environment, e)
  }

  override fun isStarting(configurationId: String, executorId: String, runnerId: String): Boolean {
    if (configurationId != "") {
      return inProgress.contains(InProgressEntry(configurationId, executorId, runnerId))
    }
    else {
      return inProgress.any { it.executorId == executorId && it.runnerId == runnerId  }
    }
  }

  private fun awaitTermination(request: Runnable, delayMillis: Long) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode) {
      app.invokeLater(request, ModalityState.any())
    }
    else {
      awaitingTerminationAlarm.addRequest(request, delayMillis)
    }
  }

  private fun getIncompatibleRunningDescriptors(configurationAndSettings: RunnerAndConfigurationSettings): List<RunContentDescriptor> {
    val configurationToCheckCompatibility = configurationAndSettings.configuration
    return getRunningDescriptors(Condition { runningConfigurationAndSettings ->
      val runningConfiguration = runningConfigurationAndSettings.configuration
      if (runningConfiguration is CompatibilityAwareRunProfile) {
        runningConfiguration.mustBeStoppedToRun(configurationToCheckCompatibility)
      }
      else {
        false
      }
    })
  }

  override fun getRunningDescriptors(condition: Condition<in RunnerAndConfigurationSettings>): List<RunContentDescriptor> {
    val result = SmartList<RunContentDescriptor>()
    for (entry in runningConfigurations) {
      if (entry.settings != null && condition.value(entry.settings)) {
        val processHandler = entry.descriptor.processHandler
        if (processHandler != null /*&& !processHandler.isProcessTerminating()*/ && !processHandler.isProcessTerminated) {
          result.add(entry.descriptor)
        }
      }
    }
    return result
  }

  fun getDescriptors(condition: Condition<in RunnerAndConfigurationSettings>): List<RunContentDescriptor> {
    val result = SmartList<RunContentDescriptor>()
    for (entry in runningConfigurations) {
      if (entry.settings != null && condition.value(entry.settings)) {
        result.add(entry.descriptor)
      }
    }
    return result
  }

  override fun getExecutors(descriptor: RunContentDescriptor): Set<Executor> {
    val result = HashSet<Executor>()
    for (entry in runningConfigurations) {
      if (descriptor === entry.descriptor) {
        result.add(entry.executor)
      }
    }
    return result
  }

  fun getConfigurations(descriptor: RunContentDescriptor): Set<RunnerAndConfigurationSettings> {
    val result = HashSet<RunnerAndConfigurationSettings>()
    for (entry in runningConfigurations) {
      val settings = entry.settings
      if (descriptor === entry.descriptor && settings != null) {
        result.add(settings)
      }
    }
    return result
  }

  fun getExecutionEnvironments(descriptor: RunContentDescriptor) =
    buildSet {
      for (entry in runningConfigurations) {
        if (entry.descriptor === descriptor) {
          add(entry.executionEnvironment)
        }
      }
    }
}

@ApiStatus.Internal
fun RunnerAndConfigurationSettings.isOfSameType(runnerAndConfigurationSettings: RunnerAndConfigurationSettings): Boolean {
  if (this === runnerAndConfigurationSettings) return true
  val thisConfiguration = configuration
  val thatConfiguration = runnerAndConfigurationSettings.configuration
  if (thisConfiguration === thatConfiguration) return true

  if (this is RunnerAndConfigurationSettingsImpl &&
      runnerAndConfigurationSettings is RunnerAndConfigurationSettingsImpl &&
      this.filePathIfRunningCurrentFile != null) {
    // These are special run configurations, which are used for running 'Current File' (a special item in the combobox). They are not stored in RunManager.
    return this.filePathIfRunningCurrentFile == runnerAndConfigurationSettings.filePathIfRunningCurrentFile
  }

  if (thisConfiguration is UserDataHolder) {
    val originalRunProfile = DELEGATED_RUN_PROFILE_KEY[thisConfiguration] ?: return false
    if (originalRunProfile === thatConfiguration) return true
    if (thatConfiguration is UserDataHolder) return originalRunProfile === DELEGATED_RUN_PROFILE_KEY[thatConfiguration]
  }
  return false
}

private fun triggerUsage(environment: ExecutionEnvironment): StructuredIdeActivity? {
  val isDumb = DumbService.isDumb(environment.project)
  val runConfiguration = environment.runnerAndConfigurationSettings?.configuration
  val configurationFactory = runConfiguration?.factory ?: return null
  val isRerun = environment.getUserData(ExecutionManagerImpl.REPORT_NEXT_START_AS_RERUN) == true
  val isServiceView = RunDashboardManager.getInstance(environment.project).isShowInDashboard(runConfiguration)

  // The 'Rerun' button in the Run tool window will reuse the same ExecutionEnvironment object again.
  // If there are no processes to stop, the REPORT_NEXT_START_AS_RERUN won't be set in restartRunProfile(), so need to set it here.
  if (!isRerun) {
    environment.putUserData(ExecutionManagerImpl.REPORT_NEXT_START_AS_RERUN, true)
  }

  return when(val parentIdeActivity = environment.getUserData(ExecutionManagerImpl.PARENT_PROFILE_IDE_ACTIVITY)) {
    null -> RunConfigurationUsageTriggerCollector
      .trigger(environment.project, configurationFactory, environment.executor, runConfiguration, isRerun,
               environment.isRunningCurrentFile, isDumb, isServiceView)
    else -> RunConfigurationUsageTriggerCollector
      .triggerWithParent(parentIdeActivity, environment.project, configurationFactory, environment.executor, runConfiguration, isRerun,
                         environment.isRunningCurrentFile, isDumb, isServiceView)
  }
}

private fun createEnvironmentBuilder(project: Project,
                                     executor: Executor,
                                     configuration: RunnerAndConfigurationSettings?): ExecutionEnvironmentBuilder {
  val builder = ExecutionEnvironmentBuilder(project, executor)

  val runner = configuration?.let { ProgramRunner.getRunner(executor.id, it.configuration) }
  if (runner == null && configuration != null) {
    ExecutionManagerImpl.LOG.error("Cannot find runner for ${configuration.name}")
  }
  else if (runner != null) {
    builder.runnerAndSettings(runner, configuration)
  }
  return builder
}

private fun userApprovesStopForSameTypeConfigurations(project: Project, configName: String, instancesCount: Int): Boolean {
  val config = RunManagerImpl.getInstanceImpl(project).config
  if (!config.isRestartRequiresConfirmation) {
    return true
  }

  @Suppress("DuplicatedCode")
  val option = object : DialogWrapper.DoNotAskOption {
    override fun isToBeShown() = config.isRestartRequiresConfirmation

    override fun setToBeShown(value: Boolean, exitCode: Int) {
      config.isRestartRequiresConfirmation = value
    }

    override fun canBeHidden() = true

    override fun shouldSaveOptionsOnCancel() = false

    override fun getDoNotShowMessage(): String {
      return UIBundle.message("dialog.options.do.not.show")
    }
  }
  return Messages.showOkCancelDialog(
    project,
    ExecutionBundle.message("rerun.singleton.confirmation.message", configName, instancesCount),
    ExecutionBundle.message("process.is.running.dialog.title", configName),
    ExecutionBundle.message("rerun.confirmation.button.text"),
    CommonBundle.getCancelButtonText(),
    Messages.getQuestionIcon(), option) == Messages.OK
}

private fun userApprovesStopForIncompatibleConfigurations(project: Project,
                                                          configName: String,
                                                          runningIncompatibleDescriptors: List<RunContentDescriptor>): Boolean {
  val config = RunManagerImpl.getInstanceImpl(project).config
  if (!config.isStopIncompatibleRequiresConfirmation) {
    return true
  }

  @Suppress("DuplicatedCode")
  val option = object : DialogWrapper.DoNotAskOption {
    override fun isToBeShown() = config.isStopIncompatibleRequiresConfirmation

    override fun setToBeShown(value: Boolean, exitCode: Int) {
      config.isStopIncompatibleRequiresConfirmation = value
    }

    override fun canBeHidden() = true

    override fun shouldSaveOptionsOnCancel() = false

    override fun getDoNotShowMessage(): String {
      return UIBundle.message("dialog.options.do.not.show")
    }
  }

  val names = StringBuilder()
  for (descriptor in runningIncompatibleDescriptors) {
    val name = descriptor.displayName
    if (names.isNotEmpty()) {
      names.append(", ")
    }
    names.append(if (name.isNullOrEmpty()) ExecutionBundle.message("run.configuration.no.name") else String.format("'%s'", name))
  }

  return Messages.showOkCancelDialog(
    project,
    ExecutionBundle.message("stop.incompatible.confirmation.message",
      configName, names.toString(), runningIncompatibleDescriptors.size),
    ExecutionBundle.message("incompatible.configuration.is.running.dialog.title", runningIncompatibleDescriptors.size),
    ExecutionBundle.message("stop.incompatible.confirmation.button.text"),
    CommonBundle.getCancelButtonText(),
    Messages.getQuestionIcon(), option) == Messages.OK
}

private class ProcessExecutionListener(private val project: Project,
                                       private val executorId: String,
                                       private val environment: ExecutionEnvironment,
                                       private val descriptor: RunContentDescriptor,
                                       private val activity: StructuredIdeActivity?) : ProcessAdapter() {
  private val willTerminateNotified = AtomicBoolean()
  private val terminateNotified = AtomicBoolean()

  override fun processTerminated(event: ProcessEvent) {
    if (project.isDisposed || !terminateNotified.compareAndSet(false, true)) {
      return
    }

    ApplicationManager.getApplication().invokeLater(Runnable {
      val ui = descriptor.runnerLayoutUi
      if (ui != null && !ui.isDisposed) {
        ui.updateActionsNow()
      }
    }, ModalityState.any(), project.disposed)

    val processHandler = event.processHandler
    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC)
      .processTerminated(executorId, environment, processHandler, event.exitCode)

    val runConfigurationFinishType = when {
      processHandler.getUserData(TERMINATING_FOR_RERUN) == true -> RunConfigurationFinishType.TERMINATED_DUE_TO_RERUN
      processHandler.getUserData(ProcessHandler.TERMINATION_REQUESTED) == true -> RunConfigurationFinishType.TERMINATED_BY_STOP
      else -> RunConfigurationFinishType.UNKNOWN
    }
    RunConfigurationUsageTriggerCollector.logProcessFinished(activity, runConfigurationFinishType)

    processHandler.removeProcessListener(this)
    SaveAndSyncHandler.getInstance().scheduleRefresh()
  }

  override fun processWillTerminate(event: ProcessEvent, shouldNotBeUsed: Boolean) {
    if (project.isDisposed || !willTerminateNotified.compareAndSet(false, true)) {
      return
    }

    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processTerminating(executorId, environment, event.processHandler)
  }
}

private data class InProgressEntry(val configId: String, val executorId: String, val runnerId: String)

private data class RunningConfigurationEntry(
  val descriptor: RunContentDescriptor,
  val executionEnvironment: ExecutionEnvironment,
  val executor: Executor
) {
  val settings: RunnerAndConfigurationSettings?
    get() = executionEnvironment.runnerAndConfigurationSettings
}

private class TargetPrepareComponent(val console: ConsoleView) : JPanel(BorderLayout()), Disposable {
  init {
    add(console.component, BorderLayout.CENTER)
  }

  @Volatile
  private var finished = false
  fun isPreparationFinished() = finished
  fun setPreparationFinished() {
    finished = true
  }

  override fun dispose() {
    Disposer.dispose(console)
  }
}

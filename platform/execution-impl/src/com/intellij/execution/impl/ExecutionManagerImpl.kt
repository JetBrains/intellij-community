// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.CommonBundle
import com.intellij.build.BuildContentManager
import com.intellij.execution.*
import com.intellij.execution.configuration.CompatibilityAwareRunProfile
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfiguration.RestartSingletonResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ExecutionManagerImpl.Companion.DELEGATED_RUN_PROFILE_KEY
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
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.AppUIUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.Alarm
import com.intellij.util.SmartList
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.*
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.BorderLayout
import java.io.OutputStream
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.SwingUtilities


class ExecutionManagerImpl(private val project: Project) : ExecutionManager(), Disposable {
  companion object {
    val LOG = logger<ExecutionManagerImpl>()
    private val EMPTY_PROCESS_HANDLERS = emptyArray<ProcessHandler>()

    internal val DELEGATED_RUN_PROFILE_KEY = Key.create<RunProfile>("DELEGATED_RUN_PROFILE_KEY")

    @JvmField
    val EXECUTION_SESSION_ID_KEY = ExecutionManager.EXECUTION_SESSION_ID_KEY

    @JvmField
    val EXECUTION_SKIP_RUN = ExecutionManager.EXECUTION_SKIP_RUN

    @JvmStatic
    fun getInstance(project: Project) = project.service<ExecutionManager>() as ExecutionManagerImpl

    @JvmStatic
    fun isProcessRunning(descriptor: RunContentDescriptor?): Boolean {
      val processHandler = descriptor?.processHandler
      return processHandler != null && !processHandler.isProcessTerminated
    }

    @JvmStatic
    fun stopProcess(descriptor: RunContentDescriptor?) {
      stopProcess(descriptor?.processHandler)
    }

    @JvmStatic
    fun stopProcess(processHandler: ProcessHandler?) {
      if (processHandler == null) {
        return
      }

      processHandler.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)

      if (processHandler is KillableProcess && processHandler.isProcessTerminating) {
        // process termination was requested, but it's still alive
        // in this case 'force quit' will be performed
        processHandler.killProcess()
        return
      }

      if (!processHandler.isProcessTerminated) {
        if (processHandler.detachIsDefault()) {
          processHandler.detachProcess()
        }
        else {
          processHandler.destroyProcess()
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
  }

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
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

  private val awaitingTerminationAlarm = Alarm()
  private val awaitingRunProfiles = HashMap<RunProfile, ExecutionEnvironment>()
  private val runningConfigurations: MutableList<RunningConfigurationEntry> = ContainerUtil.createLockFreeCopyOnWriteList()

  private val inProgress = Collections.synchronizedSet(HashSet<InProgressEntry>())

  private fun processNotStarted(environment: ExecutionEnvironment, activity: StructuredIdeActivity?, e : Throwable? = null) {
    RunConfigurationUsageTriggerCollector.logProcessFinished(activity, RunConfigurationFinishType.FAILED_TO_START)
    val executorId = environment.executor.id
    inProgress.remove(InProgressEntry(executorId, environment.runner.runnerId))
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
              descriptor.isActivateToolWindowWhenAdded = it.isActivateToolWindowBeforeRun
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
    inProgress.add(InProgressEntry(executor.id, environment.runner.runnerId))
    project.messageBus.syncPublisher(EXECUTION_TOPIC).processStartScheduled(executor.id, environment)

    val startRunnable = Runnable {
      if (project.isDisposed) {
        return@Runnable
      }

      project.messageBus.syncPublisher(EXECUTION_TOPIC).processStarting(executor.id, environment)

      fun handleError(e: Throwable) {
        processNotStarted(environment, activity, e)
        if (e !is ProcessCanceledException) {
          ProgramRunnerUtil.handleExecutionError(project, environment, e, environment.runProfile)
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

              val entry = RunningConfigurationEntry(descriptor, environment.runnerAndConfigurationSettings, executor)
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
                inProgress.remove(InProgressEntry(executor.id, environment.runner.runnerId))
                project.messageBus.syncPublisher(EXECUTION_TOPIC).processStarted(executor.id, environment, processHandler)

                val listener = ProcessExecutionListener(project, executor.id, environment, processHandler, descriptor, activity)
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

        val settings = task.settings
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
              ExecutionUtil.handleExecutionError(environment, ExecutionException(
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
            if (onCancelRunnable != null) {
              SwingUtilities.invokeLater(onCancelRunnable)
            }
            return@executeOnPooledThread
          }
        }
        catch (e: ProcessCanceledException) {
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

    // important! Do not use DumbService.smartInvokeLater here because it depends on modality state
    // and execution of startRunnable could be skipped if modality state check fails

    SwingUtilities.invokeLater {
      if (project.isDisposed) {
        return@invokeLater
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
          ExecutionUtil.handleExecutionError(environment, ExecutionException(
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
                                 environmentCustomization: Consumer<ExecutionEnvironment>?) {
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
        stopProcess(descriptor)
      }
    }

    if (awaitingRunProfiles[environment.runProfile] === environment) {
      // defense from rerunning exactly the same ExecutionEnvironment
      return
    }

    awaitingRunProfiles[environment.runProfile] = environment

    awaitTermination(object : Runnable {
      override fun run() {
        if (awaitingRunProfiles[environment.runProfile] !== environment) {
          // a new rerun has been requested before starting this one, ignore this rerun
          return
        }

        if ((configuration != null && !configuration.type.isDumbAware && DumbService.getInstance(project).isDumb)
            || inProgress.contains(InProgressEntry(environment.executor.id, environment.runner.runnerId))) {
          awaitTermination(this, 100)
          return
        }

        for (descriptor in runningOfTheSameType) {
          val processHandler = descriptor.processHandler
          if (processHandler != null && !processHandler.isProcessTerminated) {
            awaitTermination(this, 100)
            return
          }
        }

        awaitingRunProfiles.remove(environment.runProfile)

        // start() can be called during restartRunProfile() after pretty long 'awaitTermination()' so we have to check if the project is still here
        if (environment.project.isDisposed) {
          return
        }

        val settings = environment.runnerAndConfigurationSettings
        executeConfiguration(environment, settings != null && settings.isEditBeforeRun)
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
    val runnerAndConfigurationSettings = environment.runnerAndConfigurationSettings
    val project = environment.project
    var runner = environment.runner
    if (runnerAndConfigurationSettings != null) {
      val targetManager = ExecutionTargetManager.getInstance(project)
      if (!targetManager.doCanRun(runnerAndConfigurationSettings.configuration, environment.executionTarget)) {
        ExecutionUtil.handleExecutionError(environment, ExecutionException(ProgramRunnerUtil.getCannotRunOnErrorMessage( environment.runProfile, environment.executionTarget)))
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
          inProgress.add(InProgressEntry(environment.executor.id, environment.runner.runnerId))
          ReadAction.nonBlocking(Callable { RunManagerImpl.canRunConfiguration(environment) })
            .finishOnUiThread(ModalityState.NON_MODAL) { canRun ->
              inProgress.remove(InProgressEntry(environment.executor.id, environment.runner.runnerId))
              if (canRun) {
                executeConfiguration(environment, environment.runner, assignNewId, this.project, environment.runnerAndConfigurationSettings)
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

  private fun editConfigurationUntilSuccess(environment: ExecutionEnvironment, assignNewId: Boolean) {
    ReadAction.nonBlocking(Callable { RunManagerImpl.canRunConfiguration(environment) })
      .finishOnUiThread(ModalityState.NON_MODAL) { canRun ->
        val runAnyway = if (!canRun) {
          val message = ExecutionBundle.message("dialog.message.configuration.still.incorrect.do.you.want.to.edit.it.again")
          val title = ExecutionBundle.message("dialog.title.change.configuration.settings")
          Messages.showYesNoDialog(project, message, title, CommonBundle.message("button.edit"), ExecutionBundle.message("run.continue.anyway"), Messages.getErrorIcon()) != Messages.YES
        } else true
        if (canRun || runAnyway) {
          val runner = ProgramRunner.getRunner(environment.executor.id, environment.runnerAndConfigurationSettings!!.configuration)
          if (runner == null) {
            ExecutionUtil.handleExecutionError(environment,
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

  private fun executeConfiguration(environment: ExecutionEnvironment,
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
      ProgramRunnerUtil.handleExecutionError(project, environment, e, runnerAndConfigurationSettings?.configuration)
    }
  }

  override fun isStarting(executorId: String, runnerId: String): Boolean {
    return inProgress.contains(InProgressEntry(executorId, runnerId))
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

  fun getRunningDescriptors(condition: Condition<in RunnerAndConfigurationSettings>): List<RunContentDescriptor> {
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

  fun getExecutors(descriptor: RunContentDescriptor): Set<Executor> {
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
      if (descriptor === entry.descriptor && entry.settings != null) {
        result.add(entry.settings)
      }
    }
    return result
  }
}

@ApiStatus.Internal
fun RunnerAndConfigurationSettings.isOfSameType(runnerAndConfigurationSettings: RunnerAndConfigurationSettings): Boolean {
  if (this === runnerAndConfigurationSettings) return true
  val thisConfiguration = configuration
  val thatConfiguration = runnerAndConfigurationSettings.configuration
  if (thisConfiguration === thatConfiguration) return true

  if (thisConfiguration is UserDataHolder) {
    val originalRunProfile = DELEGATED_RUN_PROFILE_KEY[thisConfiguration] ?: return false
    if (originalRunProfile === thatConfiguration) return true
    if (thatConfiguration is UserDataHolder) return originalRunProfile === DELEGATED_RUN_PROFILE_KEY[thatConfiguration]
  }
  return false
}

private fun triggerUsage(environment: ExecutionEnvironment): StructuredIdeActivity? {
  val runConfiguration = environment.runnerAndConfigurationSettings?.configuration
  val configurationFactory = runConfiguration?.factory ?: return null
  return RunConfigurationUsageTriggerCollector.trigger(environment.project, configurationFactory, environment.executor, runConfiguration)
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
  @Suppress("DuplicatedCode")
  val config = RunManagerImpl.getInstanceImpl(project).config
  @Suppress("DuplicatedCode")
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
                                       private val processHandler: ProcessHandler,
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

    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processTerminated(executorId, environment, processHandler, event.exitCode)

    val runConfigurationFinishType =
      if (event.processHandler.getUserData(ProcessHandler.TERMINATION_REQUESTED) == true) RunConfigurationFinishType.TERMINATED
      else RunConfigurationFinishType.UNKNOWN
    RunConfigurationUsageTriggerCollector.logProcessFinished(activity, runConfigurationFinishType)

    processHandler.removeProcessListener(this)
    SaveAndSyncHandler.getInstance().scheduleRefresh()
  }

  override fun processWillTerminate(event: ProcessEvent, shouldNotBeUsed: Boolean) {
    if (project.isDisposed || !willTerminateNotified.compareAndSet(false, true)) {
      return
    }

    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processTerminating(executorId, environment, processHandler)
  }
}

private data class InProgressEntry(val executorId: String, val runnerId: String)

private data class RunningConfigurationEntry(val descriptor: RunContentDescriptor,
                                             val settings: RunnerAndConfigurationSettings?,
                                             val executor: Executor)

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

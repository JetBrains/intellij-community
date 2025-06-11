// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.execution

import com.intellij.execution.actions.ExecutorGroupActionGroup
import com.intellij.execution.actions.RunContextAction
import com.intellij.execution.actions.RunNewConfigurationContextAction
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.compound.SettingsAndEffectiveTarget
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl.Companion.getUniqueIdFor
import com.intellij.execution.runToolbar.*
import com.intellij.execution.runToolbar.RunToolbarProcess.Companion.getProcessesByExecutorId
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunConfigurationStartHistory
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer.LightCustomizeStrategy
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Consumer

private val LOG = logger<ExecutorRegistryImpl>()

@ApiStatus.Internal
class ExecutorRegistryImpl(coroutineScope: CoroutineScope) : ExecutorRegistry() {
  private val contextActionIdSet = HashSet<String>()
  private val idToAction = HashMap<String, AnAction>()
  private val contextActionIdToAction = HashMap<String, AnAction>()

  private val runWidgetIdToAction = HashMap<String, AnAction>()

  init {
    Executor.EXECUTOR_EXTENSION_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<Executor> {
      override fun extensionAdded(extension: Executor, pluginDescriptor: PluginDescriptor) {
        initExecutorActions(extension, ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar())
      }

      override fun extensionRemoved(extension: Executor, pluginDescriptor: PluginDescriptor) {
        deinitExecutor(extension)
      }
    })
  }

  companion object {
    const val RUNNERS_GROUP: String = "RunnerActions"
    const val RUN_CONTEXT_GROUP_MORE: String = "RunContextGroupMore"
  }

  internal class ExecutorRegistryActionConfigurationTuner : ActionConfigurationCustomizer, LightCustomizeStrategy {
    override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
      if (Executor.EXECUTOR_EXTENSION_NAME.hasAnyExtensions()) {
        val executorRegistry = serviceAsync<ExecutorRegistry>() as ExecutorRegistryImpl
        Executor.EXECUTOR_EXTENSION_NAME.forEachExtensionSafe {
          executorRegistry.initExecutorActions(it, actionRegistrar)
        }
      }
    }
  }

  @VisibleForTesting
  @Synchronized
  fun initExecutorActions(executor: Executor, actionRegistrar: ActionRuntimeRegistrar) {
    if (contextActionIdSet.contains(executor.getContextActionId())) {
      LOG.error("Executor with context action id: \"${executor.getContextActionId()}\" was already registered!")
    }

    val toolbarAction: AnAction?
    val runContextAction: AnAction?
    val runNonExistingContextAction: AnAction?
    if (executor is ExecutorGroup<*>) {
      val delegateId = "${executor.getId()}_delegate"
      val actionGroup = ExecutorGroupActionGroup(executor) {
        @Suppress("DEPRECATION")
        ExecutorAction(it)
      }
      registerAction(actionRegistrar = actionRegistrar, actionId = delegateId, anAction = actionGroup, map = idToAction)

      val toolbarActionGroup = SplitButtonAction(actionGroup)
      val presentation = toolbarActionGroup.getTemplatePresentation()
      presentation.setIconSupplier { executor.getIcon() }
      presentation.setText(executor.getStartActionText())
      presentation.setDescription(executor.getDescription())
      toolbarAction = toolbarActionGroup
      runContextAction = ExecutorGroupActionGroup(executor) { RunContextAction(it) }
      runNonExistingContextAction = ExecutorGroupActionGroup(executor) { RunNewConfigurationContextAction(it) }
    }
    else {
      @Suppress("DEPRECATION")
      toolbarAction = ExecutorAction(executor)
      runContextAction = RunContextAction(executor)
      runNonExistingContextAction = RunNewConfigurationContextAction(executor)
    }

    registerActionInGroup(
      actionRegistrar = actionRegistrar,
      actionId = executor.getId(),
      anAction = toolbarAction,
      groupId = RUNNERS_GROUP,
      map = idToAction,
    )

    val action: AnAction = registerAction(actionRegistrar, executor.getContextActionId(), runContextAction, contextActionIdToAction)
    if (isExecutorInMainGroup(executor)) {
      val group = actionRegistrar.getActionOrStub(RUN_CONTEXT_EXECUTORS_GROUP) as DefaultActionGroup
      actionRegistrar.addToGroup(group, action, Constraints.LAST)
    }
    else {
      val group = actionRegistrar.getActionOrStub(RUN_CONTEXT_GROUP_MORE) as DefaultActionGroup
      actionRegistrar.addToGroup(group, action, Constraints(Anchor.BEFORE, "CreateRunConfiguration"))
    }

    val nonExistingAction = registerAction(
      actionRegistrar = actionRegistrar,
      actionId = newConfigurationContextActionId(executor),
      anAction = runNonExistingContextAction,
      map = contextActionIdToAction,
    )
    val group = actionRegistrar.getActionOrStub(RUN_CONTEXT_GROUP_MORE) as DefaultActionGroup
    actionRegistrar.addToGroup(group, nonExistingAction, Constraints(Anchor.BEFORE, "CreateNewRunConfiguration"))

    initRunToolbarExecutorActions(executor = executor, actionRegistrar = actionRegistrar, runWidgetIdToAction = runWidgetIdToAction)

    contextActionIdSet.add(executor.getContextActionId())
  }


  @VisibleForTesting
  @Synchronized
  fun deinitExecutor(executor: Executor) {
    contextActionIdSet.remove(executor.getContextActionId())

    val actionManager = ActionManager.getInstance()
    unregisterAction(executor.getId(), RUNNERS_GROUP, idToAction, actionManager)
    if (executor is ExecutorGroup<*>) {
      unregisterAction(executor.getId() + "_delegate", RUNNERS_GROUP, idToAction, actionManager)
    }
    if (isExecutorInMainGroup(executor)) {
      unregisterAction(executor.getContextActionId(), RUN_CONTEXT_EXECUTORS_GROUP, contextActionIdToAction, actionManager)
    }
    else {
      unregisterAction(executor.getContextActionId(), RUN_CONTEXT_GROUP_MORE, contextActionIdToAction, actionManager)
    }
    unregisterAction(newConfigurationContextActionId(executor), RUN_CONTEXT_GROUP_MORE, contextActionIdToAction, actionManager)

    for (process in getProcessesByExecutorId(executor.getId())) {
      unregisterAction(process.actionId, RunToolbarProcess.RUN_WIDGET_GROUP, runWidgetIdToAction, actionManager)
      unregisterAction(process.getMainActionId(), RunToolbarProcess.RUN_WIDGET_MAIN_GROUP, runWidgetIdToAction, actionManager)
      if (executor is ExecutorGroup<*>) {
        unregisterAction(RunToolbarAdditionActionsHolder.getAdditionActionId(process), process.moreActionSubGroupName,
                         runWidgetIdToAction, actionManager)
        unregisterAction(RunToolbarAdditionActionsHolder.getAdditionActionChooserGroupId(process), process.moreActionSubGroupName,
                         runWidgetIdToAction, actionManager)
      }
    }
  }

  override fun getExecutorById(executorId: String): Executor? {
    // even IJ Ultimate with all plugins has ~7 executors - linear search is ok here
    for (executor in Executor.EXECUTOR_EXTENSION_NAME.extensionList) {
      if (executorId == executor.getId()) {
        return executor
      }
      else if (executor is ExecutorGroup<*> && executorId.startsWith(executor.getId())) {
        for (child in executor.childExecutors()) {
          if (executorId == child.getId()) {
            return child
          }
        }
      }
    }
    return null
  }

  @Deprecated("Use {@link com.intellij.execution.actions.ExecutorAction} instead ")
  open class ExecutorAction(executor: Executor) : com.intellij.execution.actions.ExecutorAction(executor)

  object RunnerHelper {
    @JvmStatic
    fun run(
      project: Project,
      configuration: RunConfiguration?,
      settings: RunnerAndConfigurationSettings?,
      dataContext: DataContext,
      executor: Executor,
    ) {
      if (settings != null) {
        RunConfigurationStartHistory.getInstance(project).register(settings)
      }
      runSubProcess(
        project = project,
        configuration = configuration,
        settings = settings,
        dataContext = dataContext,
        executor = executor,
        environmentCustomization = RunToolbarProcessData.prepareBaseSettingCustomization(settings, null),
      )
    }

    @JvmStatic
    fun runSubProcess(
      project: Project,
      configuration: RunConfiguration?,
      settings: RunnerAndConfigurationSettings?,
      dataContext: DataContext,
      executor: Executor,
      environmentCustomization: Consumer<in ExecutionEnvironment>?,
    ) {
      if (configuration is CompoundRunConfiguration) {
        val runManager = RunManager.getInstance(project)
        for (settingsAndEffectiveTarget in configuration.getConfigurationsWithEffectiveRunTargets()) {
          val subConfiguration = settingsAndEffectiveTarget.configuration
          runSubProcess(
            project = project,
            configuration = subConfiguration,
            settings = runManager.findSettings(subConfiguration),
            dataContext = dataContext,
            executor = executor,
            environmentCustomization = environmentCustomization,
          )
        }
      }
      else {
        var builder = settings?.let { ExecutionEnvironmentBuilder.createOrNull(executor, it) } ?: return
        val rtData = dataContext.getData(RunToolbarData.RUN_TOOLBAR_DATA_KEY)
        if (rtData == null) {
          builder = builder.activeTarget()
        }
        else {
          val target = rtData.executionTarget
          builder = if (target == null) builder.activeTarget() else builder.target(target)
        }

        val environment = builder.dataContext(dataContext).build()
        environmentCustomization?.accept(environment)
        ExecutionManager.getInstance(project).restartRunProfile(environment)
      }
    }

    @JvmOverloads
    @JvmStatic
    fun canRun(
      project: Project,
      executor: Executor,
      configuration: RunConfiguration,
      isStartingTracker: Ref<Boolean>? = null,
    ): Boolean {
      val pairs = if (configuration is CompoundRunConfiguration) {
        configuration.getConfigurationsWithEffectiveRunTargets()
      }
      else {
        val target = ExecutionTargetManager.getActiveTarget(project)
        listOf(SettingsAndEffectiveTarget(configuration, target))
      }
      isStartingTracker?.set(false)
      return canRun(project = project, pairs = pairs, executor = executor, isStartingTracker = isStartingTracker)
    }

    private fun canRun(
      project: Project,
      pairs: List<SettingsAndEffectiveTarget>,
      executor: Executor,
      isStartingTracker: Ref<Boolean>?,
    ): Boolean {
      if (pairs.isEmpty()) {
        return false
      }

      for (pair in pairs) {
        val configuration = pair.configuration
        if (configuration is CompoundRunConfiguration) {
          if (!canRun(
              project = project,
              pairs = configuration.getConfigurationsWithEffectiveRunTargets(),
              executor = executor,
              isStartingTracker = isStartingTracker,
            )) {
            return false
          }
          continue
        }

        val runner = ProgramRunner.getRunner(executor.getId(), configuration)
        if (runner == null || !ExecutionTargetManager.canRun(configuration, pair.target)) {
          return false
        }
        else if (ExecutionManager.getInstance(project).isStarting(getUniqueIdFor(configuration), executor.getId(), runner.getRunnerId())) {
          if (isStartingTracker == null) {
            return false
          }
          else {
            isStartingTracker.set(true)
          }
        }
      }
      return true
    }
  }
}

private fun initRunToolbarExecutorActions(executor: Executor, actionRegistrar: ActionRuntimeRegistrar, runWidgetIdToAction: HashMap<String, AnAction>) {
  if (!ToolbarSettings.getInstance().isAvailable) {
    return
  }

  for (process in getProcessesByExecutorId(executor.getId())) {
    if (executor is ExecutorGroup<*>) {
      if (process.showInBar) {
        val wrappedAction = RunToolbarExecutorGroupAction(RunToolbarExecutorGroup(executor, { RunToolbarGroupProcessAction(process, it) }, process))
        val presentation = wrappedAction.getTemplatePresentation()
        presentation.setIcon(executor.getIcon())
        presentation.setText(process.name)
        presentation.setDescription(executor.getDescription())

        registerActionInGroup(
          actionRegistrar = actionRegistrar,
          actionId = process.actionId,
          anAction = wrappedAction,
          groupId = RunToolbarProcess.RUN_WIDGET_GROUP,
          map = runWidgetIdToAction,
        )
      }
      else {
        val holder = RunToolbarAdditionActionsHolder(executor, process)

        registerActionInGroup(
          actionRegistrar = actionRegistrar,
          actionId = RunToolbarAdditionActionsHolder.getAdditionActionId(process),
          anAction = holder.additionAction,
          groupId = process.moreActionSubGroupName,
          map = runWidgetIdToAction,
        )
        registerActionInGroup(
          actionRegistrar = actionRegistrar,
          actionId = RunToolbarAdditionActionsHolder.getAdditionActionChooserGroupId(process),
          anAction = holder.moreActionChooserGroup,
          groupId = process.moreActionSubGroupName,
          map = runWidgetIdToAction,
        )
      }
    }
    else if (!process.isTemporaryProcess() && process.showInBar) {
      val wrappedAction = RunToolbarProcessAction(process, executor)
      val wrappedMainAction = RunToolbarProcessMainAction(process, executor)

      registerActionInGroup(
        actionRegistrar = actionRegistrar,
        actionId = process.actionId,
        anAction = wrappedAction,
        groupId = RunToolbarProcess.RUN_WIDGET_GROUP,
        map = runWidgetIdToAction,
      )

      registerActionInGroup(
        actionRegistrar = actionRegistrar,
        actionId = process.getMainActionId(),
        anAction = wrappedMainAction,
        groupId = RunToolbarProcess.RUN_WIDGET_MAIN_GROUP,
        map = runWidgetIdToAction,
      )
    }
  }
}

private const val RUN_CONTEXT_EXECUTORS_GROUP: String = "RunContextExecutorsGroup"

private fun newConfigurationContextActionId(executor: Executor): @NonNls String = "newConfiguration${executor.getContextActionId()}"

private fun isExecutorInMainGroup(executor: Executor): Boolean {
  val id = executor.getId()
  return id == ToolWindowId.RUN || id == ToolWindowId.DEBUG || !Registry.`is`("executor.actions.submenu", true)
}

private fun registerActionInGroup(
  actionRegistrar: ActionRuntimeRegistrar,
  actionId: String,
  anAction: AnAction,
  groupId: String,
  map: MutableMap<String, AnAction>,
) {
  val action = registerAction(actionRegistrar, actionId, anAction, map)
  val group = actionRegistrar.getActionOrStub(groupId)
  if (group != null) {
    actionRegistrar.addToGroup(group, action, Constraints.LAST)
  }
}

private fun registerAction(
  actionRegistrar: ActionRuntimeRegistrar,
  actionId: String,
  anAction: AnAction,
  map: MutableMap<String, AnAction>,
): AnAction {
  var action = actionRegistrar.getActionOrStub(actionId)
  if (action == null) {
    actionRegistrar.registerAction(actionId, anAction)
    map.put(actionId, anAction)
    action = anAction
  }
  return action
}

private fun unregisterAction(
  actionId: String,
  groupId: String,
  map: MutableMap<String, AnAction>,
  actionManager: ActionManager,
) {
  val group = actionManager.getAction(groupId) as DefaultActionGroup? ?: return
  var action = map.get(actionId)
  if (action == null) {
    action = actionManager.getAction(actionId)
    if (action != null) {
      group.remove(action, actionManager)
    }
  }
  else {
    actionManager.unregisterAction(actionId)
    map.remove(actionId)
  }
}
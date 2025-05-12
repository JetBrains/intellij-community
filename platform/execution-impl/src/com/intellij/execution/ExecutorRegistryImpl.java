// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.actions.ExecutorGroupActionGroup;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.actions.RunNewConfigurationContextAction;
import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.compound.SettingsAndEffectiveTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runToolbar.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunConfigurationStartHistory;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.JavaCoroutines;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ExecutorRegistryImpl extends ExecutorRegistry {
  private static final Logger LOG = Logger.getInstance(ExecutorRegistryImpl.class);

  public static final String RUNNERS_GROUP = "RunnerActions";
  public static final String RUN_CONTEXT_GROUP = "RunContextGroupInner";
  public static final String RUN_CONTEXT_GROUP_MORE = "RunContextGroupMore";
  public static final String RUN_CONTEXT_EXECUTORS_GROUP = "RunContextExecutorsGroup";

  private final Set<String> contextActionIdSet = new HashSet<>();
  private final Map<String, AnAction> idToAction = new HashMap<>();
  private final Map<String, AnAction> contextActionIdToAction = new HashMap<>();

  private final Map<String, AnAction> runWidgetIdToAction = new HashMap<>();

  public ExecutorRegistryImpl() {
    Executor.EXECUTOR_EXTENSION_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull Executor extension, @NotNull PluginDescriptor pluginDescriptor) {
        //noinspection TestOnlyProblems
        initExecutorActions(extension, ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar());
      }

      @Override
      public void extensionRemoved(@NotNull Executor extension, @NotNull PluginDescriptor pluginDescriptor) {
        deinitExecutor(extension);
      }
    }, null);
  }

  static final class ExecutorRegistryActionConfigurationTuner implements ActionConfigurationCustomizer,
                                                                         ActionConfigurationCustomizer.LightCustomizeStrategy {
    @Override
    public @Nullable Object customize(@NotNull ActionRuntimeRegistrar actionRegistrar, @NotNull Continuation<? super Unit> $completion) {
      return JavaCoroutines.suspendJava(jc -> {
        if (Executor.EXECUTOR_EXTENSION_NAME.hasAnyExtensions()) {
          ((ExecutorRegistryImpl)getInstance()).init(actionRegistrar);
        }
        jc.resume(Unit.INSTANCE);
      }, $completion);
    }
  }

  @TestOnly
  public synchronized void initExecutorActions(@NotNull Executor executor, @NotNull ActionRuntimeRegistrar actionRegistrar) {
    if (contextActionIdSet.contains(executor.getContextActionId())) {
      LOG.error("Executor with context action id: \"" + executor.getContextActionId() + "\" was already registered!");
    }

    AnAction toolbarAction;
    AnAction runContextAction;
    AnAction runNonExistingContextAction;
    if (executor instanceof ExecutorGroup<?> executorGroup) {
      String delegateId = executor.getId() + "_delegate";
      ExecutorGroupActionGroup actionGroup = new ExecutorGroupActionGroup(executorGroup, ExecutorAction::new);
      registerAction(actionRegistrar, delegateId, actionGroup, idToAction);

      ActionGroup toolbarActionGroup = new SplitButtonAction(actionGroup);
      Presentation presentation = toolbarActionGroup.getTemplatePresentation();
      presentation.setIconSupplier(executor::getIcon);
      presentation.setText(executor.getStartActionText());
      presentation.setDescription(executor.getDescription());
      toolbarAction = toolbarActionGroup;
      runContextAction = new ExecutorGroupActionGroup(executorGroup, RunContextAction::new);
      runNonExistingContextAction = new ExecutorGroupActionGroup(executorGroup, RunNewConfigurationContextAction::new);
    }
    else {
      toolbarAction = new ExecutorAction(executor);
      runContextAction = new RunContextAction(executor);
      runNonExistingContextAction = new RunNewConfigurationContextAction(executor);
    }

    registerActionInGroup(actionRegistrar, executor.getId(), toolbarAction, RUNNERS_GROUP, idToAction);

    AnAction action = registerAction(actionRegistrar, executor.getContextActionId(), runContextAction, contextActionIdToAction);
    if (isExecutorInMainGroup(executor)) {
      DefaultActionGroup group = Objects.requireNonNull((DefaultActionGroup)actionRegistrar.getActionOrStub(RUN_CONTEXT_EXECUTORS_GROUP));
      actionRegistrar.addToGroup(group, action, Constraints.LAST);
    }
    else {
      DefaultActionGroup group = Objects.requireNonNull((DefaultActionGroup)actionRegistrar.getActionOrStub(RUN_CONTEXT_GROUP_MORE));
      actionRegistrar.addToGroup(group, action, new Constraints(Anchor.BEFORE, "CreateRunConfiguration"));
    }

    AnAction nonExistingAction = registerAction(actionRegistrar, newConfigurationContextActionId(executor), runNonExistingContextAction,
                                                contextActionIdToAction);
    DefaultActionGroup group = Objects.requireNonNull((DefaultActionGroup)actionRegistrar.getActionOrStub(RUN_CONTEXT_GROUP_MORE));
    actionRegistrar.addToGroup(group, nonExistingAction, new Constraints(Anchor.BEFORE, "CreateNewRunConfiguration"));

    initRunToolbarExecutorActions(executor, actionRegistrar);

    contextActionIdSet.add(executor.getContextActionId());
  }

  private synchronized void initRunToolbarExecutorActions(@NotNull Executor executor, @NotNull ActionRuntimeRegistrar actionRegistrar) {
    if (ToolbarSettings.getInstance().isAvailable()) {
      RunToolbarProcess.getProcessesByExecutorId(executor.getId()).forEach(process -> {
        if (executor instanceof ExecutorGroup<?> executorGroup) {

          if (process.getShowInBar()) {
            ActionGroup wrappedAction = new RunToolbarExecutorGroupAction(
              new RunToolbarExecutorGroup(executorGroup, (ex) -> new RunToolbarGroupProcessAction(process, ex), process));
            Presentation presentation = wrappedAction.getTemplatePresentation();
            presentation.setIcon(executor.getIcon());
            presentation.setText(process.getName());
            presentation.setDescription(executor.getDescription());

            registerActionInGroup(actionRegistrar, process.getActionId(), wrappedAction, RunToolbarProcess.RUN_WIDGET_GROUP,
                                  runWidgetIdToAction);
          }
          else {
            RunToolbarAdditionActionsHolder holder = new RunToolbarAdditionActionsHolder(executorGroup, process);

            registerActionInGroup(actionRegistrar, RunToolbarAdditionActionsHolder.getAdditionActionId(process), holder.getAdditionAction(),
                                  process.getMoreActionSubGroupName(),
                                  runWidgetIdToAction);
            registerActionInGroup(actionRegistrar, RunToolbarAdditionActionsHolder.getAdditionActionChooserGroupId(process),
                                  holder.getMoreActionChooserGroup(), process.getMoreActionSubGroupName(),
                                  runWidgetIdToAction);
          }
        }
        else {
          if (!process.isTemporaryProcess() && process.getShowInBar()) {
            AnAction wrappedAction = new RunToolbarProcessAction(process, executor);
            AnAction wrappedMainAction = new RunToolbarProcessMainAction(process, executor);

            registerActionInGroup(actionRegistrar, process.getActionId(), wrappedAction, RunToolbarProcess.RUN_WIDGET_GROUP,
                                  runWidgetIdToAction);

            registerActionInGroup(actionRegistrar, process.getMainActionId(), wrappedMainAction, RunToolbarProcess.RUN_WIDGET_MAIN_GROUP,
                                  runWidgetIdToAction);
          }
        }
      });
    }
  }

  private static @NonNls String newConfigurationContextActionId(@NotNull Executor executor) {
    return "newConfiguration" + executor.getContextActionId();
  }

  private static boolean isExecutorInMainGroup(@NotNull Executor executor) {
    String id = executor.getId();
    return id.equals(ToolWindowId.RUN) || id.equals(ToolWindowId.DEBUG) || !Registry.is("executor.actions.submenu", true);
  }

  private static void registerActionInGroup(@NotNull ActionRuntimeRegistrar actionRegistrar,
                                            @NotNull String actionId,
                                            @NotNull AnAction anAction,
                                            @NotNull String groupId,
                                            @NotNull Map<String, AnAction> map) {
    AnAction action = registerAction(actionRegistrar, actionId, anAction, map);
    AnAction group = actionRegistrar.getActionOrStub(groupId);
    if (group != null) {
      actionRegistrar.addToGroup(group, action, Constraints.LAST);
    }
  }

  private static @NotNull AnAction registerAction(@NotNull ActionRuntimeRegistrar actionRegistrar,
                                                  @NotNull String actionId,
                                                  @NotNull AnAction anAction,
                                                  @NotNull Map<String, AnAction> map) {
    AnAction action = actionRegistrar.getActionOrStub(actionId);
    if (action == null) {
      actionRegistrar.registerAction(actionId, anAction);
      map.put(actionId, anAction);
      action = anAction;
    }
    return action;
  }

  @VisibleForTesting
  public synchronized void deinitExecutor(@NotNull Executor executor) {
    contextActionIdSet.remove(executor.getContextActionId());

    ActionManager actionManager = ActionManager.getInstance();
    unregisterAction(executor.getId(), RUNNERS_GROUP, idToAction, actionManager);
    if (executor instanceof ExecutorGroup<?>) {
      unregisterAction(executor.getId() + "_delegate", RUNNERS_GROUP, idToAction, actionManager);
    }
    if (isExecutorInMainGroup(executor)) {
      unregisterAction(executor.getContextActionId(), RUN_CONTEXT_EXECUTORS_GROUP, contextActionIdToAction, actionManager);
    }
    else {
      unregisterAction(executor.getContextActionId(), RUN_CONTEXT_GROUP_MORE, contextActionIdToAction, actionManager);
    }
    unregisterAction(newConfigurationContextActionId(executor), RUN_CONTEXT_GROUP_MORE, contextActionIdToAction, actionManager);

    RunToolbarProcess.getProcessesByExecutorId(executor.getId()).forEach(process -> {
      unregisterAction(process.getActionId(), RunToolbarProcess.RUN_WIDGET_GROUP, runWidgetIdToAction, actionManager);
      unregisterAction(process.getMainActionId(), RunToolbarProcess.RUN_WIDGET_MAIN_GROUP, runWidgetIdToAction, actionManager);

      if (executor instanceof ExecutorGroup) {
        unregisterAction(RunToolbarAdditionActionsHolder.getAdditionActionId(process), process.getMoreActionSubGroupName(),
                         runWidgetIdToAction, actionManager);
        unregisterAction(RunToolbarAdditionActionsHolder.getAdditionActionChooserGroupId(process), process.getMoreActionSubGroupName(),
                         runWidgetIdToAction, actionManager);
      }
    });
  }

  private static void unregisterAction(@NotNull String actionId,
                                       @NotNull String groupId,
                                       @NotNull Map<String, AnAction> map,
                                       @NotNull ActionManager actionManager) {
    DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction(groupId);
    if (group == null) {
      return;
    }

    AnAction action = map.get(actionId);
    if (action == null) {
      action = actionManager.getAction(actionId);
      if (action != null) {
        group.remove(action, actionManager);
      }
    }
    else {
      actionManager.unregisterAction(actionId);
      map.remove(actionId);
    }
  }

  @Override
  public Executor getExecutorById(@NotNull String executorId) {
    // even IJ Ultimate with all plugins has ~7 executors - linear search is ok here
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      if (executorId.equals(executor.getId())) {
        return executor;
      }
      else if (executor instanceof ExecutorGroup<?> && executorId.startsWith(executor.getId())) {
        for (Executor child : ((ExecutorGroup<?>)executor).childExecutors()) {
          if (executorId.equals(child.getId())) {
            return child;
          }
        }
      }
    }
    return null;
  }

  private void init(@NotNull ActionRuntimeRegistrar actionRegistrar) {
    Executor.EXECUTOR_EXTENSION_NAME.forEachExtensionSafe(executor -> {
      //noinspection TestOnlyProblems
      initExecutorActions(executor, actionRegistrar);
    });
  }

  /** @deprecated Use {@link com.intellij.execution.actions.ExecutorAction} instead */
  @Deprecated(forRemoval = true)
  public static class ExecutorAction extends com.intellij.execution.actions.ExecutorAction {
    public ExecutorAction(@NotNull Executor executor) {
      super(executor);
    }
  }

  public static final class RunnerHelper {
    public static void run(@NotNull Project project,
                           @Nullable RunConfiguration configuration,
                           @Nullable RunnerAndConfigurationSettings settings,
                           @NotNull DataContext dataContext,
                           @NotNull Executor executor) {
      if (settings != null) {
        RunConfigurationStartHistory.getInstance(project).register(settings);
      }
      runSubProcess(project, configuration, settings, dataContext, executor, RunToolbarProcessData.prepareBaseSettingCustomization(settings, null));
    }

    public static void runSubProcess(@NotNull Project project,
                                     @Nullable RunConfiguration configuration,
                                     @Nullable RunnerAndConfigurationSettings settings,
                                     @NotNull DataContext dataContext,
                                     @NotNull Executor executor,
                                     @Nullable Consumer<? super ExecutionEnvironment> environmentCustomization) {

      if (configuration instanceof CompoundRunConfiguration) {
        RunManager runManager = RunManager.getInstance(project);
        for (SettingsAndEffectiveTarget settingsAndEffectiveTarget : ((CompoundRunConfiguration)configuration)
          .getConfigurationsWithEffectiveRunTargets()) {
          RunConfiguration subConfiguration = settingsAndEffectiveTarget.getConfiguration();
          runSubProcess(project, subConfiguration, runManager.findSettings(subConfiguration), dataContext, executor, environmentCustomization);
        }
      }
      else {
        ExecutionEnvironmentBuilder builder = settings == null ? null : ExecutionEnvironmentBuilder.createOrNull(executor, settings);
        if (builder == null) {
          return;
        }

        RunToolbarData rtData = dataContext.getData(RunToolbarData.RUN_TOOLBAR_DATA_KEY);
        if(rtData != null) {
          ExecutionTarget target = rtData.getExecutionTarget();
          builder = target == null ?  builder.activeTarget() : builder.target(target);
        }
        else {
          builder = builder.activeTarget();
        }

        ExecutionEnvironment environment = builder.dataContext(dataContext).build();
        if(environmentCustomization != null) environmentCustomization.accept(environment);
        ExecutionManager.getInstance(project).restartRunProfile(environment);
      }
    }

    public static boolean canRun(@NotNull Project project,
                                 @NotNull Executor executor,
                                 @NotNull RunConfiguration configuration) {
      return canRun(project, executor, configuration, null);
    }

    public static boolean canRun(@NotNull Project project,
                                 @NotNull Executor executor,
                                 @NotNull RunConfiguration configuration,
                                 @Nullable Ref<Boolean> isStartingTracker) {
      List<SettingsAndEffectiveTarget> pairs;
      if (configuration instanceof CompoundRunConfiguration) {
        pairs = ((CompoundRunConfiguration)configuration).getConfigurationsWithEffectiveRunTargets();
      }
      else {
        ExecutionTarget target = ExecutionTargetManager.getActiveTarget(project);
        pairs = Collections.singletonList(new SettingsAndEffectiveTarget(configuration, target));
      }
      if (isStartingTracker != null) isStartingTracker.set(false);
      return canRun(project, pairs, executor, isStartingTracker);
    }

    private static boolean canRun(@NotNull Project project,
                                  @NotNull List<SettingsAndEffectiveTarget> pairs,
                                  @NotNull Executor executor,
                                  @Nullable Ref<Boolean> isStartingTracker) {
      if (pairs.isEmpty()) {
        return false;
      }

      for (SettingsAndEffectiveTarget pair : pairs) {
        RunConfiguration configuration = pair.getConfiguration();
        if (configuration instanceof CompoundRunConfiguration o) {
          if (!canRun(project, o.getConfigurationsWithEffectiveRunTargets(), executor, isStartingTracker)) {
            return false;
          }
          continue;
        }

        ProgramRunner<?> runner = ProgramRunner.getRunner(executor.getId(), configuration);
        if (runner == null || !ExecutionTargetManager.canRun(configuration, pair.getTarget())) {
          return false;
        }
        else if (ExecutionManager.getInstance(project).isStarting(
          RunnerAndConfigurationSettingsImpl.getUniqueIdFor(configuration),
          executor.getId(), runner.getRunnerId())) {
          if (isStartingTracker != null) isStartingTracker.set(true);
          else return false;
        }
      }
      return true;
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.actions.RunNewConfigurationContextAction;
import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.compound.SettingsAndEffectiveTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.ExecutionManagerImplKt;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess;
import com.intellij.execution.stateWidget.StateWidget;
import com.intellij.execution.stateWidget.StateWidgetAdditionActionsHolder;
import com.intellij.execution.stateWidget.StateWidgetGroup;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;

public final class ExecutorRegistryImpl extends ExecutorRegistry {
  private static final Logger LOG = Logger.getInstance(ExecutorRegistryImpl.class);

  public static final String RUNNERS_GROUP = "RunnerActions";
  public static final String RUN_CONTEXT_GROUP = "RunContextGroupInner";
  public static final String RUN_CONTEXT_GROUP_MORE = "RunContextGroupMore";

  private final Set<String> myContextActionIdSet = new HashSet<>();
  private final Map<String, AnAction> myIdToAction = new HashMap<>();
  private final Map<String, AnAction> myContextActionIdToAction = new HashMap<>();
  private final Map<String, AnAction> myStateWidgetIdToAction = new HashMap<>();

  public ExecutorRegistryImpl() {
    Executor.EXECUTOR_EXTENSION_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull Executor extension, @NotNull PluginDescriptor pluginDescriptor) {
        //noinspection TestOnlyProblems
        initExecutorActions(extension, ActionManager.getInstance());
      }

      @Override
      public void extensionRemoved(@NotNull Executor extension, @NotNull PluginDescriptor pluginDescriptor) {
        deinitExecutor(extension);
      }
    }, null);
  }

  final static class ExecutorRegistryActionConfigurationTuner implements ActionConfigurationCustomizer {
    @Override
    public void customize(@NotNull ActionManager manager) {
      if (Executor.EXECUTOR_EXTENSION_NAME.hasAnyExtensions()) {
        ((ExecutorRegistryImpl)getInstance()).init(manager);
      }
    }
  }

  @TestOnly
  public synchronized void initExecutorActions(@NotNull Executor executor, @NotNull ActionManager actionManager) {
    if (myContextActionIdSet.contains(executor.getContextActionId())) {
      LOG.error("Executor with context action id: \"" + executor.getContextActionId() + "\" was already registered!");
    }

    AnAction toolbarAction;
    AnAction runContextAction;
    AnAction runNonExistingContextAction;
    if (executor instanceof ExecutorGroup) {
      ExecutorGroup<?> executorGroup = (ExecutorGroup<?>)executor;
      ActionGroup toolbarActionGroup = new SplitButtonAction(new ExecutorGroupActionGroup(executorGroup, ExecutorAction::new));
      Presentation presentation = toolbarActionGroup.getTemplatePresentation();
      presentation.setIcon(executor.getIcon());
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

    Executor.ActionWrapper customizer = executor.runnerActionsGroupExecutorActionCustomizer();
    registerActionInGroup(actionManager, executor.getId(), customizer == null ? toolbarAction : customizer.wrap(toolbarAction), RUNNERS_GROUP, myIdToAction);

    AnAction action = registerAction(actionManager, executor.getContextActionId(), runContextAction, myContextActionIdToAction);
    if (isExecutorInMainGroup(executor)) {
      ((DefaultActionGroup)actionManager.getAction(RUN_CONTEXT_GROUP))
        .add(action, new Constraints(Anchor.BEFORE, RUN_CONTEXT_GROUP_MORE), actionManager);
    }
    else {
      ((DefaultActionGroup)actionManager.getAction(RUN_CONTEXT_GROUP_MORE))
        .add(action, new Constraints(Anchor.BEFORE, "CreateRunConfiguration"), actionManager);
    }
    
    AnAction nonExistingAction = registerAction(actionManager, newConfigurationContextActionId(executor), runNonExistingContextAction, myContextActionIdToAction);
    ((DefaultActionGroup)actionManager.getAction(RUN_CONTEXT_GROUP_MORE))
      .add(nonExistingAction, new Constraints(Anchor.BEFORE, "CreateNewRunConfiguration"), actionManager);

    if(StateWidgetProcess.isAvailable()) {
      StateWidgetProcess.getProcessesByExecutorId(executor.getId()).forEach(process -> {
        if (executor instanceof ExecutorGroup) {
          ExecutorGroup<?> executorGroup = (ExecutorGroup<?>)executor;
          if(process.getShowInBar()) {
            ActionGroup wrappedAction = new SplitButtonAction(new StateWidgetGroup(executorGroup, ExecutorAction::new, process));
            Presentation presentation = wrappedAction.getTemplatePresentation();
            presentation.setIcon(executor.getIcon());
            presentation.setText(process.getName());
            presentation.setDescription(executor.getDescription());

            registerActionInGroup(actionManager, process.getActionId(), wrappedAction, StateWidgetProcess.STATE_WIDGET_GROUP,
                                  myStateWidgetIdToAction);
          }

          StateWidgetAdditionActionsHolder holder = new StateWidgetAdditionActionsHolder(executorGroup, process);

          registerActionInGroup(actionManager, StateWidgetAdditionActionsHolder.getAdditionActionId(process), holder.getAdditionAction(), process.getMoreActionSubGroupName(),
                                myStateWidgetIdToAction);
          registerActionInGroup(actionManager, StateWidgetAdditionActionsHolder.getAdditionActionChooserGroupId(process), holder.getMoreActionChooserGroup(), process.getMoreActionSubGroupName(),
                                myStateWidgetIdToAction);
        }
        else {
          ExecutorAction wrappedAction = new StateWidget(executor, process);
          registerActionInGroup(actionManager, process.getActionId(), wrappedAction, StateWidgetProcess.STATE_WIDGET_GROUP,
                                myStateWidgetIdToAction);
        }
      });
    }

    myContextActionIdSet.add(executor.getContextActionId());
  }

  @NonNls
  private static String newConfigurationContextActionId(@NotNull Executor executor) {
    return "newConfiguration" + executor.getContextActionId();
  }

  private static boolean isExecutorInMainGroup(@NotNull Executor executor) {
    return !Registry.is("executor.actions.submenu") || executor.getId().equals(ToolWindowId.RUN) || executor.getId().equals(ToolWindowId.DEBUG);
  }

  private static void registerActionInGroup(@NotNull ActionManager actionManager, @NotNull String actionId, @NotNull AnAction anAction, @NotNull String groupId, @NotNull Map<String, AnAction> map) {
    AnAction action = registerAction(actionManager, actionId, anAction, map);
    ((DefaultActionGroup)actionManager.getAction(groupId)).add(action, actionManager);
  }

  @NotNull
  private static AnAction registerAction(@NotNull ActionManager actionManager,
                                         @NotNull String actionId,
                                         @NotNull AnAction anAction,
                                         @NotNull Map<String, AnAction> map) {
    AnAction action = actionManager.getAction(actionId);
    if (action == null) {
      actionManager.registerAction(actionId, anAction);
      map.put(actionId, anAction);
      action = anAction;
    }
    return action;
  }

  synchronized void deinitExecutor(@NotNull Executor executor) {
    myContextActionIdSet.remove(executor.getContextActionId());

    unregisterAction(executor.getId(), RUNNERS_GROUP, myIdToAction);
    if (isExecutorInMainGroup(executor)) {
      unregisterAction(executor.getContextActionId(), RUN_CONTEXT_GROUP, myContextActionIdToAction);
    }
    else {
      unregisterAction(executor.getContextActionId(), RUN_CONTEXT_GROUP_MORE, myContextActionIdToAction);
    }
    unregisterAction(newConfigurationContextActionId(executor), RUN_CONTEXT_GROUP_MORE, myContextActionIdToAction);

    StateWidgetProcess.getProcessesByExecutorId(executor.getId()).forEach(process -> {
      unregisterAction(process.getActionId(), StateWidgetProcess.STATE_WIDGET_GROUP, myStateWidgetIdToAction);
      if (executor instanceof ExecutorGroup) {
        unregisterAction(StateWidgetAdditionActionsHolder.getAdditionActionId(process), process.getMoreActionSubGroupName(),
                         myStateWidgetIdToAction);
        unregisterAction(StateWidgetAdditionActionsHolder.getAdditionActionChooserGroupId(process), process.getMoreActionSubGroupName(),
                         myStateWidgetIdToAction);
      }
    });
  }

  private static void unregisterAction(@NotNull String actionId, @NotNull String groupId, @NotNull Map<String, AnAction> map) {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction(groupId);
    if (group == null) {
      return;
    }

    AnAction action = map.get(actionId);
    if (action != null) {
      group.remove(action, actionManager);
      actionManager.unregisterAction(actionId);
      map.remove(actionId);
    }
    else {
      action = ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        group.remove(action, actionManager);
      }
    }
  }

  @Override
  public Executor getExecutorById(@NotNull String executorId) {
    // even IJ Ultimate with all plugins has ~7 executors - linear search is ok here
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      if (executorId.equals(executor.getId())) {
        return executor;
      }
    }
    return null;
  }

  private void init(@NotNull ActionManager actionManager) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      try {
        //noinspection TestOnlyProblems
        initExecutorActions(executor, actionManager);
      }
      catch (Throwable t) {
        LOG.error("executor initialization failed: " + executor.getClass().getName(), t);
      }
    }
  }

  public static class ExecutorAction extends AnAction implements DumbAware, UpdateInBackground {
    protected final Executor myExecutor;

    protected ExecutorAction(@NotNull Executor executor) {
      super(executor.getStartActionText(), executor.getDescription(), IconLoader.createLazy(() -> executor.getIcon()));
      myExecutor = executor;
    }

    private boolean canRun(@NotNull Project project, @NotNull List<SettingsAndEffectiveTarget> pairs) {
      return RunnerHelper.canRun(project, pairs, myExecutor);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      Project project = e.getProject();
      if (project == null || !project.isInitialized() || project.isDisposed()) {
        presentation.setEnabled(false);
        return;
      }

      RunnerAndConfigurationSettings selectedSettings = getSelectedConfiguration(project);
      boolean enabled = false;
      boolean hideDisabledExecutorButtons = false;
      String text;
      if (selectedSettings != null) {
        if (DumbService.isDumb(project) && !selectedSettings.getType().isDumbAware()) {
          presentation.setEnabled(false);
          return;
        }

        presentation.setIcon(getInformativeIcon(project, selectedSettings));
        RunConfiguration configuration = selectedSettings.getConfiguration();
        if (configuration instanceof CompoundRunConfiguration) {
          enabled = canRun(project, ((CompoundRunConfiguration)configuration).getConfigurationsWithEffectiveRunTargets());
        }
        else {
          ExecutionTarget target = ExecutionTargetManager.getActiveTarget(project);
          enabled = canRun(project, Collections.singletonList(new SettingsAndEffectiveTarget(configuration, target)));
          hideDisabledExecutorButtons = configuration.hideDisabledExecutorButtons();
        }
        if (enabled) {
          presentation.setDescription(myExecutor.getDescription());
        }
        text = myExecutor.getStartActionText(configuration.getName());
      }
      else {
        text = getTemplatePresentation().getTextWithMnemonic();
      }

      if (hideDisabledExecutorButtons) {
        presentation.setEnabledAndVisible(enabled);
      }
      else {
        presentation.setEnabled(enabled);
      }

      if (presentation.isVisible()) {
        presentation.setVisible(myExecutor.isApplicable(project));
      }
      presentation.setText(text);
    }

    private Icon getInformativeIcon(@NotNull Project project, @NotNull RunnerAndConfigurationSettings selectedConfiguration) {
      ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(project);
      RunConfiguration configuration = selectedConfiguration.getConfiguration();
      if (configuration instanceof RunnerIconProvider) {
        RunnerIconProvider provider = (RunnerIconProvider)configuration;
        Icon icon = provider.getExecutorIcon(configuration, myExecutor);
        if (icon != null) {
          return icon;
        }
      }

      List<RunContentDescriptor> runningDescriptors =
        executionManager.getRunningDescriptors(s -> ExecutionManagerImplKt.isOfSameType(s, selectedConfiguration));
      runningDescriptors = ContainerUtil.filter(runningDescriptors, descriptor -> executionManager.getExecutors(descriptor).contains(myExecutor));

      if (!configuration.isAllowRunningInParallel() && !runningDescriptors.isEmpty() && DefaultRunExecutor.EXECUTOR_ID.equals(myExecutor.getId())) {
        return AllIcons.Actions.Restart;
      }
      if (runningDescriptors.isEmpty()) {
        return myExecutor.getIcon();
      }

      if (runningDescriptors.size() == 1) {
        return ExecutionUtil.getLiveIndicator(myExecutor.getIcon());
      }
      else {
        return IconUtil.addText(myExecutor.getIcon(), Integer.toString(runningDescriptors.size()));
      }
    }

    @Nullable
    private static RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull Project project) {
      return RunManager.getInstance(project).getSelectedConfiguration();
    }

    private void run(@NotNull Project project, @Nullable RunConfiguration configuration, @Nullable RunnerAndConfigurationSettings settings, @NotNull DataContext dataContext) {
      RunnerHelper.run(project, configuration, settings, dataContext, myExecutor);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      if (project == null || project.isDisposed()) {
        return;
      }

      MacroManager.getInstance().cacheMacrosPreview(e.getDataContext());
      RunnerAndConfigurationSettings selectedConfiguration = getSelectedConfiguration(project);
      if (selectedConfiguration != null) {
        run(project, selectedConfiguration.getConfiguration(), selectedConfiguration, e.getDataContext());
      }
    }
  }

  // TODO: make private as soon as IDEA-207986 will be fixed
  // RunExecutorSettings configurations can be modified, so we request current childExecutors on each AnAction#update call
  public static class ExecutorGroupActionGroup extends ActionGroup implements DumbAware {
    protected final ExecutorGroup<?> myExecutorGroup;
    private final Function<? super Executor, ? extends AnAction> myChildConverter;

    protected ExecutorGroupActionGroup(@NotNull ExecutorGroup<?> executorGroup, @NotNull Function<? super Executor, ? extends AnAction> childConverter) {
      myExecutorGroup = executorGroup;
      myChildConverter = childConverter;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      List<Executor> childExecutors = myExecutorGroup.childExecutors();
      AnAction[] result = new AnAction[childExecutors.size()];
      for (int i = 0; i < childExecutors.size(); i++) {
        result[i] = myChildConverter.apply(childExecutors.get(i));
      }
      return result;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      if (project == null || !project.isInitialized() || project.isDisposed()) {
        e.getPresentation().setEnabled(false);
        return;
      }
      e.getPresentation().setEnabledAndVisible(myExecutorGroup.isApplicable(project));
    }
  }

  public static final class RunnerHelper {
    public static void run(@NotNull Project project,
                            @Nullable RunConfiguration configuration,
                            @Nullable RunnerAndConfigurationSettings settings,
                            @NotNull DataContext dataContext,
                            @NotNull Executor executor) {
      if (configuration instanceof CompoundRunConfiguration) {
        RunManager runManager = RunManager.getInstance(project);
        for (SettingsAndEffectiveTarget settingsAndEffectiveTarget : ((CompoundRunConfiguration)configuration)
          .getConfigurationsWithEffectiveRunTargets()) {
          RunConfiguration subConfiguration = settingsAndEffectiveTarget.getConfiguration();
          run(project, subConfiguration, runManager.findSettings(subConfiguration), dataContext, executor);
        }
      }
      else {
        ExecutionEnvironmentBuilder builder = settings == null ? null : ExecutionEnvironmentBuilder.createOrNull(executor, settings);
        if (builder == null) {
          return;
        }
        ExecutionManager.getInstance(project).restartRunProfile(builder.activeTarget().dataContext(dataContext).build());
      }
    }

    public static boolean canRun(@NotNull Project project, @NotNull List<SettingsAndEffectiveTarget> pairs, @NotNull Executor executor) {
      if (pairs.isEmpty()) {
        return false;
      }

      for (SettingsAndEffectiveTarget pair : pairs) {
        RunConfiguration configuration = pair.getConfiguration();
        if (configuration instanceof CompoundRunConfiguration) {
          if (!canRun(project, ((CompoundRunConfiguration)configuration).getConfigurationsWithEffectiveRunTargets(), executor)) {
            return false;
          }
          continue;
        }

        ProgramRunner<?> runner = ProgramRunner.getRunner(executor.getId(), configuration);
        if (runner == null
            || !ExecutionTargetManager.canRun(configuration, pair.getTarget())
            || ExecutionManager.getInstance(project).isStarting(executor.getId(), runner.getRunnerId())) {
          return false;
        }
      }
      return true;
    }
  }
}

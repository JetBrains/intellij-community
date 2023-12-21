// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.actions.*;
import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.compound.SettingsAndEffectiveTarget;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.ExecutionManagerImplKt;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runToolbar.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.actionSystem.remoting.ActionRemotePermissionRequirements;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SpinningProgressIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.JavaCoroutines;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.ui.JBUI;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.emptyList;

public final class ExecutorRegistryImpl extends ExecutorRegistry {
  private static final Logger LOG = Logger.getInstance(ExecutorRegistryImpl.class);

  public static final String RUNNERS_GROUP = "RunnerActions";
  public static final String RUN_CONTEXT_GROUP = "RunContextGroupInner";
  public static final String RUN_CONTEXT_GROUP_MORE = "RunContextGroupMore";

  private static final Key<SpinningProgressIcon> spinningIconKey = Key.create("spinning-icon-key");

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
      ActionGroup toolbarActionGroup = new SplitButtonAction(new ExecutorGroupActionGroup(executorGroup, ExecutorAction::new));
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

    Executor.ActionWrapper customizer = executor.runnerActionsGroupExecutorActionCustomizer();
    registerActionInGroup(actionRegistrar, executor.getId(), customizer == null ? toolbarAction : customizer.wrap(toolbarAction), RUNNERS_GROUP,
                          idToAction);

    AnAction action = registerAction(actionRegistrar, executor.getContextActionId(), runContextAction, contextActionIdToAction);
    if (isExecutorInMainGroup(executor)) {
      DefaultActionGroup group = Objects.requireNonNull((DefaultActionGroup)actionRegistrar.getActionOrStub(RUN_CONTEXT_GROUP));
      actionRegistrar.addToGroup(group, action, new Constraints(Anchor.BEFORE, RUN_CONTEXT_GROUP_MORE));
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
            ExecutorAction wrappedAction = new RunToolbarProcessAction(process, executor);
            ExecutorAction wrappedMainAction = new RunToolbarProcessMainAction(process, executor);

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

  synchronized void deinitExecutor(@NotNull Executor executor) {
    contextActionIdSet.remove(executor.getContextActionId());

    ActionManager actionManager = ActionManager.getInstance();
    unregisterAction(executor.getId(), RUNNERS_GROUP, idToAction, actionManager);
    if (isExecutorInMainGroup(executor)) {
      unregisterAction(executor.getContextActionId(), RUN_CONTEXT_GROUP, contextActionIdToAction, actionManager);
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

  public static class ExecutorAction extends AnAction implements DumbAware,
                                                                 ActionRemotePermissionRequirements.RunAccess {
    private static final Key<RunCurrentFileInfo> CURRENT_FILE_RUN_CONFIGS_KEY = Key.create("CURRENT_FILE_RUN_CONFIGS");

    protected final Executor myExecutor;

    protected ExecutorAction(@NotNull Executor executor) {
      super(executor::getStartActionText, executor::getDescription, executor::getIcon);

      myExecutor = executor;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.putClientProperty(ExecutorActionStatus.KEY, null); // by default
      Project project = e.getProject();
      if (project == null || !project.isInitialized() || project.isDisposed()) {
        presentation.setEnabled(false);
        return;
      }

      RunnerAndConfigurationSettings selectedSettings = getSelectedConfiguration(e);
      boolean enabled = false;
      ExecutorActionStatus actionStatus = null;
      boolean runConfigAsksToHideDisabledExecutorButtons = false;
      String text;
      if (selectedSettings != null) {
        if (DumbService.isDumb(project) && !selectedSettings.getType().isDumbAware()) {
          presentation.setEnabled(false);
          return;
        }

        actionStatus = setupActionStatus(e, project, selectedSettings, presentation);
        presentation.setIcon(getInformativeIcon(project, selectedSettings, e));
        RunConfiguration configuration = selectedSettings.getConfiguration();
        if (!isSuppressed(project)) {
          enabled = RunnerHelper.canRun(project, myExecutor, configuration);
        }
        if (!(configuration instanceof CompoundRunConfiguration)) {
          runConfigAsksToHideDisabledExecutorButtons = configuration.hideDisabledExecutorButtons();
        }
        if (enabled) {
          presentation.setDescription(myExecutor.getDescription());
        }

        if (ExperimentalUI.isNewUI() &&
            needRerunPresentation(selectedSettings.getConfiguration(), getRunningDescriptors(project, selectedSettings))) {
          if (myExecutor.getId().equals(DefaultRunExecutor.EXECUTOR_ID)) {
            text = ExecutionBundle.message("run.toolbar.widget.rerun.text", configuration);
          }
          else {
            text = ExecutionBundle.message("run.toolbar.widget.restart.text", myExecutor.getActionName(), configuration.getName());
          }
        }
        else {
          text = myExecutor.getStartActionText(configuration.getName());
        }
      }
      else {
        if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
          RunCurrentFileActionStatus status = getRunCurrentFileActionStatus(e, false);
          for (RunnerAndConfigurationSettings config : status.myRunConfigs) {
            actionStatus = setupActionStatus(e, project, config, presentation);
            if (actionStatus != ExecutorActionStatus.NORMAL) {
              break;
            }
          }
          enabled = status.myEnabled;
          text = status.myTooltip;
          presentation.setIcon(status.myIcon);
        }
        else {
          text = getTemplatePresentation().getTextWithMnemonic();
        }
      }

      if (actionStatus != ExecutorActionStatus.LOADING && (runConfigAsksToHideDisabledExecutorButtons || hideDisabledExecutorButtons())) {
        presentation.setEnabledAndVisible(enabled);
      }
      else {
        presentation.setEnabled(enabled);
      }

      if (presentation.isVisible()) {
        presentation.setVisible(myExecutor.isApplicable(project));
      }
      presentation.putClientProperty(ExecutorActionStatus.KEY, actionStatus);
      presentation.setText(text);
    }

    private ExecutorActionStatus setupActionStatus(@NotNull AnActionEvent e,
                                                   Project project,
                                                   RunnerAndConfigurationSettings selectedSettings,
                                                   Presentation presentation) {
      // We can consider to add spinning to the inlined run actions. But there is a problem with redrawing
      ExecutorActionStatus status = ExecutorActionStatus.NORMAL;
      if (ActionPlaces.NEW_UI_RUN_TOOLBAR.equals(e.getPlace())) {
        RunStatusHistory startHistory = RunStatusHistory.getInstance(project);

        boolean isLoading = startHistory.firstOrNull(selectedSettings, it -> (it.getExecutorId().equals(myExecutor.getId()) &&
                                                                              it.getState() == RunState.SCHEDULED)) != null;
        if (isLoading) {
          status = ExecutorActionStatus.LOADING;
          SpinningProgressIcon spinningIcon = presentation.getClientProperty(spinningIconKey);
          if (spinningIcon == null) {
            spinningIcon = new SpinningProgressIcon();
            spinningIcon.setIconColor(JBUI.CurrentTheme.RunWidget.ICON_COLOR);
            presentation.putClientProperty(spinningIconKey, spinningIcon);
          }
          presentation.setDisabledIcon(spinningIcon);
        } else {
          if (!getRunningDescriptors(project, selectedSettings).isEmpty()) {
            status = ExecutorActionStatus.RUNNING;
          }
          presentation.putClientProperty(spinningIconKey, null);
          presentation.setDisabledIcon(null);
        }
      }
      return status;
    }

    protected boolean hideDisabledExecutorButtons() {
      return false;
    }

    private @NotNull RunCurrentFileActionStatus getRunCurrentFileActionStatus(@NotNull AnActionEvent e, boolean resetCache) {
      Project project = Objects.requireNonNull(e.getProject());

      VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
      if (files.length == 1) {
        // There's only one visible editor, let's use the file from this editor, even if the editor is not in focus.
        PsiFile psiFile = PsiManager.getInstance(project).findFile(files[0]);
        if (psiFile == null) {
          String tooltip = ExecutionBundle.message("run.button.on.toolbar.tooltip.current.file.not.runnable");
          return RunCurrentFileActionStatus.createDisabled(tooltip, myExecutor.getIcon());
        }

        return getRunCurrentFileActionStatus(psiFile, resetCache, e);
      }

      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor == null) {
        String tooltip = ExecutionBundle.message("run.button.on.toolbar.tooltip.current.file.no.focused.editor");
        return RunCurrentFileActionStatus.createDisabled(tooltip, myExecutor.getIcon());
      }

      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      VirtualFile vFile = psiFile != null ? psiFile.getVirtualFile() : null;
      if (psiFile == null || vFile == null || !ArrayUtil.contains(vFile, files)) {
        // This is probably a special editor, like Python Console, which we don't want to use for the 'Run Current File' feature.
        String tooltip = ExecutionBundle.message("run.button.on.toolbar.tooltip.current.file.no.focused.editor");
        return RunCurrentFileActionStatus.createDisabled(tooltip, myExecutor.getIcon());
      }

      return getRunCurrentFileActionStatus(psiFile, resetCache, e);
    }

    private @NotNull RunCurrentFileActionStatus getRunCurrentFileActionStatus(@NotNull PsiFile psiFile, boolean resetCache,
                                                                              @NotNull AnActionEvent e) {
      List<RunnerAndConfigurationSettings> runConfigs = getRunConfigsForCurrentFile(psiFile, resetCache);
      if (runConfigs.isEmpty()) {
        String tooltip = ExecutionBundle.message("run.button.on.toolbar.tooltip.current.file.not.runnable");
        return RunCurrentFileActionStatus.createDisabled(tooltip, myExecutor.getIcon());
      }

      List<RunnerAndConfigurationSettings> runnableConfigs = filterConfigsThatHaveRunner(runConfigs);
      if (runnableConfigs.isEmpty()) {
        return RunCurrentFileActionStatus.createDisabled(myExecutor.getStartActionText(psiFile.getName()), myExecutor.getIcon());
      }

      Icon icon = myExecutor.getIcon();
      if (runnableConfigs.size() == 1) {
        icon = getInformativeIcon(psiFile.getProject(), runnableConfigs.get(0), e);
      }
      else {
        // myExecutor.getIcon() is the least preferred icon
        // AllIcons.Actions.Restart is more preferred
        // Other icons are the most preferred ones (like ExecutionUtil.getLiveIndicator())
        for (RunnerAndConfigurationSettings config : runnableConfigs) {
          Icon anotherIcon = getInformativeIcon(psiFile.getProject(), config, e);
          if (icon == myExecutor.getIcon() || (anotherIcon != myExecutor.getIcon() && anotherIcon != AllIcons.Actions.Restart)) {
            icon = anotherIcon;
          }
        }
      }

      return RunCurrentFileActionStatus.createEnabled(myExecutor.getStartActionText(psiFile.getName()), icon, runnableConfigs);
    }

    @ApiStatus.Internal
    public static List<RunnerAndConfigurationSettings> getRunConfigsForCurrentFile(@NotNull PsiFile psiFile, boolean resetCache) {
      if (resetCache) {
        psiFile.putUserData(CURRENT_FILE_RUN_CONFIGS_KEY, null);
      }

      // Without this cache, an expensive method `ConfigurationContext.getConfigurationsFromContext()` is called too often for 2 reasons:
      // - there are several buttons on the toolbar (Run, Debug, Profile, etc.), each runs ExecutorAction.update() during each action update session
      // - the state of the buttons on the toolbar is updated several times a second, even if no files are being edited

      // The following few lines do pretty much the same as CachedValuesManager.getCachedValue(), but it's implemented without calling that
      // method because it appeared to be too hard to satisfy both IdempotenceChecker.checkEquivalence() and CachedValueStabilityChecker.checkProvidersEquivalent().
      // The reason is that RunnerAndConfigurationSettings class doesn't implement equals(), and that CachedValueProvider would need to capture
      // ConfigurationContext, which doesn't implement equals() either.
      // Effectively, we need only one boolean value: whether the action is enabled or not, so it shouldn't be a problem that
      // RunnerAndConfigurationSettings and ConfigurationContext don't implement equals() and this code doesn't pass CachedValuesManager checks.

      long psiModCount = PsiModificationTracker.getInstance(psiFile.getProject()).getModificationCount();
      RunCurrentFileInfo cache = psiFile.getUserData(CURRENT_FILE_RUN_CONFIGS_KEY);

      if (cache == null || cache.myPsiModCount != psiModCount) {
        // The 'Run current file' feature doesn't depend on the caret position in the file, that's why ConfigurationContext is created like this.
        ConfigurationContext configurationContext = new ConfigurationContext(psiFile);

        // The 'Run current file' feature doesn't reuse existing run configurations (by design).
        List<ConfigurationFromContext> configurationsFromContext = configurationContext.createConfigurationsFromContext();

        List<RunnerAndConfigurationSettings> runConfigs = configurationsFromContext == null
                                                          ? List.of()
                                                          : ContainerUtil.map(configurationsFromContext,
                                                                              ConfigurationFromContext::getConfigurationSettings);

        VirtualFile vFile = psiFile.getVirtualFile();
        if (!runConfigs.isEmpty()) {
          String filePath = vFile == null ? null : vFile.getPath();
          for (RunnerAndConfigurationSettings config : runConfigs) {
            ((RunnerAndConfigurationSettingsImpl)config).setFilePathIfRunningCurrentFile(filePath);
          }
        }

        cache = new RunCurrentFileInfo(psiModCount, runConfigs);
        psiFile.putUserData(CURRENT_FILE_RUN_CONFIGS_KEY, cache);
      }

      return cache.myRunConfigs;
    }

    private @NotNull List<RunnerAndConfigurationSettings> filterConfigsThatHaveRunner(@NotNull List<? extends RunnerAndConfigurationSettings> runConfigs) {
      return ContainerUtil.filter(runConfigs, config -> ProgramRunner.getRunner(myExecutor.getId(), config.getConfiguration()) != null);
    }

    private static boolean isSuppressed(Project project) {
      for (ExecutionActionSuppressor suppressor : ExecutionActionSuppressor.EP_NAME.getExtensionList()) {
        if (suppressor.isSuppressed(project)) return true;
      }
      return false;
    }

    protected Icon getInformativeIcon(@NotNull Project project, @NotNull RunnerAndConfigurationSettings selectedConfiguration,
                                      @NotNull AnActionEvent e) {
      RunConfiguration configuration = selectedConfiguration.getConfiguration();
      if (configuration instanceof RunnerIconProvider provider) {
        Icon icon = provider.getExecutorIcon(configuration, myExecutor);
        if (icon != null) {
          return icon;
        }
      }

      List<RunContentDescriptor> runningDescriptors = getRunningDescriptors(project, selectedConfiguration);

      if (needRerunPresentation(configuration, runningDescriptors)) {
        if (ExperimentalUI.isNewUI() && myExecutor.getIcon() != myExecutor.getRerunIcon()) {
          return myExecutor.getRerunIcon();
        }
        if (DefaultRunExecutor.EXECUTOR_ID.equals(myExecutor.getId())) {
          return AllIcons.Actions.Restart;
        }
      }
      if (runningDescriptors.isEmpty()) {
        return myExecutor.getIcon();
      }

      if (runningDescriptors.size() == 1) {
        return ExecutionUtil.getLiveIndicator(myExecutor.getIcon());
      }
      else {
        return IconUtil.addText(myExecutor.getIcon(), RunToolbarPopupKt.runCounterToString(e, runningDescriptors.size()));
      }
    }

    private static boolean needRerunPresentation(RunConfiguration configuration, List<RunContentDescriptor> runningDescriptors) {
      return !configuration.isAllowRunningInParallel() && !runningDescriptors.isEmpty();
    }

    private @NotNull List<RunContentDescriptor> getRunningDescriptors(@NotNull Project project,
                                                                      @NotNull RunnerAndConfigurationSettings selectedConfiguration) {
      ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstanceIfCreated(project);
      if (executionManager == null) return emptyList();

      List<RunContentDescriptor> runningDescriptors =
        executionManager.getRunningDescriptors(s -> ExecutionManagerImplKt.isOfSameType(s, selectedConfiguration));
      runningDescriptors = ContainerUtil.filter(runningDescriptors, descriptor -> executionManager.getExecutors(descriptor).contains(myExecutor));
      return runningDescriptors;
    }

    protected @Nullable RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      RunManager runManager = project == null ? null : RunManager.getInstanceIfCreated(project);
      return runManager == null ? null : runManager.getSelectedConfiguration();
    }

    protected void run(@NotNull Project project, @NotNull RunnerAndConfigurationSettings settings, @NotNull DataContext dataContext) {
      RunnerHelper.run(project, settings.getConfiguration(), settings, dataContext, myExecutor);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getProject();
      if (project == null || project.isDisposed()) {
        return;
      }

      RunnerAndConfigurationSettings selectedConfiguration = getSelectedConfiguration(e);
      if (selectedConfiguration != null) {
        run(project, selectedConfiguration, e.getDataContext());
      }
      else if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
        runCurrentFile(e);
      }
    }

    private void runCurrentFile(@NotNull AnActionEvent e) {
      Project project = Objects.requireNonNull(e.getProject());
      List<RunnerAndConfigurationSettings> runConfigs = getRunCurrentFileActionStatus(e, true).myRunConfigs;
      if (runConfigs.isEmpty()) {
        return;
      }

      if (runConfigs.size() == 1) {
        doRunCurrentFile(project, runConfigs.get(0), e.getDataContext());
        return;
      }

      IPopupChooserBuilder<RunnerAndConfigurationSettings> builder = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(runConfigs)
        .setRenderer(new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            RunnerAndConfigurationSettings runConfig = (RunnerAndConfigurationSettings)value;
            JLabel result = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            result.setIcon(runConfig.getConfiguration().getIcon());
            result.setText(runConfig.getName());
            return result;
          }
        })
        .setItemChosenCallback(runConfig -> doRunCurrentFile(project, runConfig, e.getDataContext()));

      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        builder.createPopup().showUnderneathOf(inputEvent.getComponent());
      }
      else {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
          // Not expected to happen because we are running a file from the current editor.
          LOG.warn("Run Current File (" + runConfigs + "): getSelectedTextEditor() == null");
          return;
        }

        builder
          .setTitle(myExecutor.getActionName())
          .createPopup()
          .showInBestPositionFor(editor);
      }
    }

    protected void doRunCurrentFile(@NotNull Project project,
                                    @NotNull RunnerAndConfigurationSettings runConfig,
                                    @NotNull DataContext dataContext) {
      ExecutionUtil.doRunConfiguration(runConfig, myExecutor, null, null, dataContext, env -> env.setRunningCurrentFile(true));
    }
  }

  public static final class RunSpecifiedConfigExecutorAction extends ExecutorAction {
    private final RunnerAndConfigurationSettings myRunConfig;
    private final boolean myEditConfigBeforeRun;

    public RunSpecifiedConfigExecutorAction(@NotNull Executor executor,
                                            @NotNull RunnerAndConfigurationSettings runConfig,
                                            boolean editConfigBeforeRun) {
      super(executor);
      myRunConfig = runConfig;
      myEditConfigBeforeRun = editConfigBeforeRun;
    }

    @Override
    protected @NotNull RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull AnActionEvent e) {
      return myRunConfig;
    }

    @Override
    protected boolean hideDisabledExecutorButtons() {
      // no need in a list of disabled actions in the secondary menu of the Run Configuration item in the combo box drop-down menu.
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      if (myEditConfigBeforeRun) {
        Presentation presentation = e.getPresentation();
        presentation.setText(ExecutionBundle.message("choose.run.popup.edit"));
        presentation.setDescription(ExecutionBundle.message("choose.run.popup.edit.description"));
        presentation.setIcon(!ExperimentalUI.isNewUI() ? AllIcons.Actions.EditSource : null);
      }
    }

    @Override
    protected void run(@NotNull Project project, @NotNull RunnerAndConfigurationSettings settings, @NotNull DataContext dataContext) {
      LOG.assertTrue(myRunConfig == settings);

      if (myEditConfigBeforeRun) {
        String dialogTitle = ExecutionBundle.message("dialog.title.edit.configuration.settings");
        if (!RunDialog.editConfiguration(project, myRunConfig, dialogTitle, myExecutor)) {
          return;
        }
      }

      super.run(project, myRunConfig, dataContext);

      RunManager.getInstance(project).setSelectedConfiguration(myRunConfig);
    }
  }

  public static class RunCurrentFileExecutorAction extends ExecutorAction {
    public RunCurrentFileExecutorAction(@NotNull Executor executor) {
      super(executor);
    }

    @Override
    protected @Nullable RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull AnActionEvent e) {
      return null; // null means 'run current file, not the selected run configuration'
    }

    @Override
    protected boolean hideDisabledExecutorButtons() {
      // no need in a list of disabled actions in the secondary menu of the 'Current File' item in the combo box drop-down menu.
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      if (e.getProject() == null || !RunConfigurationsComboBoxAction.hasRunCurrentFileItem(e.getProject())) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      super.update(e);
    }
  }

  public static final class EditRunConfigAndRunCurrentFileExecutorAction extends RunCurrentFileExecutorAction {
    public EditRunConfigAndRunCurrentFileExecutorAction(@NotNull Executor executor) {
      super(executor);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      Presentation presentation = e.getPresentation();
      presentation.setText(ExecutionBundle.message("run.configurations.popup.edit.run.config.and.run.current.file"));
      presentation.setDescription(ExecutionBundle.message("run.configurations.popup.edit.run.config.and.run.current.file.description"));
      presentation.setIcon(AllIcons.Actions.EditSource);
    }

    @Override
    protected void doRunCurrentFile(@NotNull Project project,
                                    @NotNull RunnerAndConfigurationSettings runConfig,
                                    @NotNull DataContext dataContext) {
      String suggestedName = StringUtil.notNullize(((LocatableConfiguration)runConfig.getConfiguration()).suggestedName(),
                                                   runConfig.getName());
      List<String> usedNames = ContainerUtil.map(RunManager.getInstance(project).getAllSettings(), RunnerAndConfigurationSettings::getName);
      String uniqueName = UniqueNameGenerator.generateUniqueName(suggestedName, "", "", " (", ")", s -> !usedNames.contains(s));
      runConfig.setName(uniqueName);

      String dialogTitle = ExecutionBundle.message("dialog.title.edit.configuration.settings");
      if (RunDialog.editConfiguration(project, runConfig, dialogTitle, myExecutor)) {
        RunManager.getInstance(project).setTemporaryConfiguration(runConfig);
        super.doRunCurrentFile(project, runConfig, dataContext);
      }
    }
  }

  private static final class RunCurrentFileInfo {
    private final long myPsiModCount;
    private final @NotNull List<RunnerAndConfigurationSettings> myRunConfigs;

    private RunCurrentFileInfo(long psiModCount, @NotNull List<RunnerAndConfigurationSettings> runConfigs) {
      myPsiModCount = psiModCount;
      myRunConfigs = runConfigs;
    }
  }

  @ApiStatus.Internal
  public static class ExecutorGroupActionGroup extends ActionGroup implements DumbAware {
    protected final ExecutorGroup<?> myExecutorGroup;
    private final Function<? super Executor, ? extends AnAction> myChildConverter;

    protected ExecutorGroupActionGroup(@NotNull ExecutorGroup<?> executorGroup,
                                       @NotNull Function<? super Executor, ? extends AnAction> childConverter) {
      myExecutorGroup = executorGroup;
      myChildConverter = childConverter;
      Presentation presentation = getTemplatePresentation();
      presentation.setText(executorGroup.getStartActionText());
      presentation.setIconSupplier(executorGroup::getIcon);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      // RunExecutorSettings configurations can be modified, so we request current childExecutors on each call
      List<Executor> childExecutors = myExecutorGroup.childExecutors();
      AnAction[] result = new AnAction[childExecutors.size()];
      for (int i = 0; i < childExecutors.size(); i++) {
        result[i] = myChildConverter.apply(childExecutors.get(i));
      }
      return result;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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

    public static boolean canRun(@NotNull Project project, @NotNull Executor executor, RunConfiguration configuration) {
      List<SettingsAndEffectiveTarget> pairs;
      if (configuration instanceof CompoundRunConfiguration) {
        pairs = ((CompoundRunConfiguration)configuration).getConfigurationsWithEffectiveRunTargets();
      }
      else {
        ExecutionTarget target = ExecutionTargetManager.getActiveTarget(project);
        pairs = Collections.singletonList(new SettingsAndEffectiveTarget(configuration, target));
      }
      return canRun(project, pairs, executor);
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

  private static final class RunCurrentFileActionStatus {
    private final boolean myEnabled;
    private final @Nls @NotNull String myTooltip;
    private final @NotNull Icon myIcon;

    private final @NotNull List<RunnerAndConfigurationSettings> myRunConfigs;

    private static RunCurrentFileActionStatus createDisabled(@Nls @NotNull String tooltip, @NotNull Icon icon) {
      return new RunCurrentFileActionStatus(false, tooltip, icon, emptyList());
    }

    private static RunCurrentFileActionStatus createEnabled(@Nls @NotNull String tooltip,
                                                            @NotNull Icon icon,
                                                            @NotNull List<RunnerAndConfigurationSettings> runConfigs) {
      return new RunCurrentFileActionStatus(true, tooltip, icon, runConfigs);
    }

    private RunCurrentFileActionStatus(boolean enabled,
                                       @Nls @NotNull String tooltip,
                                       @NotNull Icon icon,
                                       @NotNull List<RunnerAndConfigurationSettings> runConfigs) {
      myEnabled = enabled;
      myTooltip = tooltip;
      myIcon = icon;
      myRunConfigs = runConfigs;
    }
  }
}

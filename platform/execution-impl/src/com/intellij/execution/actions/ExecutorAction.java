// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.ExecutionManagerImplKt;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunState;
import com.intellij.execution.ui.RunStatusHistory;
import com.intellij.execution.ui.RunToolbarPopupKt;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ide.core.permissions.Permission;
import com.intellij.platform.ide.core.permissions.RequiresPermissions;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SpinningProgressIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.execution.PermissionsKt.getFullRunAccess;
import static java.util.Collections.emptyList;

@ApiStatus.Internal
public class ExecutorAction extends AnAction implements DumbAware, RequiresPermissions, ActionIdProvider {

  public static final Key<Boolean> WOULD_BE_ENABLED_BUT_STARTING = Key.create("WOULD_BE_ENABLED_BUT_STARTING");

  private static final Logger LOG = Logger.getInstance(ExecutorAction.class);

  private static final Key<SpinningProgressIcon> spinningIconKey = Key.create("spinning-icon-key");
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

    RunManager runManager = RunManager.getInstanceIfCreated(project);
    if (runManager == null) {
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
      Ref<Boolean> isStartingTracker = Ref.create(false);
      if (!isSuppressed(project)) {
        enabled = ExecutorRegistryImpl.RunnerHelper.canRun(project, myExecutor, configuration, isStartingTracker);
        if (enabled && isStartingTracker.get() == Boolean.TRUE) {
          enabled = false;
          presentation.putClientProperty(WOULD_BE_ENABLED_BUT_STARTING, true);
        }
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
        ProgramRunner<RunnerSettings> runner = ProgramRunner.getRunner(getId(), configuration);
        String actionText = runner == null ? null : runner.getStartActionText(myExecutor, configuration);
        text = actionText != null ? actionText : myExecutor.getStartActionText(configuration.getName());
      }
    }
    else {
      if (RunConfigurationsComboBoxAction.hasRunCurrentFileItem(project)) {
        // don't compute the current file to run if editors are not yet loaded
        if (!project.isDefault() && !StartupManager.getInstance(project).postStartupActivityPassed()) {
          presentation.setEnabled(false);
          return;
        }

        RunCurrentFileActionStatus status = getRunCurrentFileActionStatus(e, false);
        for (RunnerAndConfigurationSettings config : status.myRunConfigs) {
          actionStatus = setupActionStatus(e, project, config, presentation);
          if (actionStatus != ExecutorActionStatus.NORMAL) {
            break;
          }
        }
        enabled = status.enabled;
        text = status.tooltip;
        presentation.setIcon(status.icon);
      }
      else {
        text = getTemplatePresentation().getTextWithMnemonic();
      }
    }

    if (actionStatus != ExecutorActionStatus.LOADING && runConfigAsksToHideDisabledExecutorButtons) {
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
    // We can consider adding spinning to the inlined run actions. But there is a problem with redrawing
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
      }
      else {
        if (!getRunningDescriptors(project, selectedSettings).isEmpty()) {
          status = ExecutorActionStatus.RUNNING;
        }
        presentation.putClientProperty(spinningIconKey, null);
        presentation.setDisabledIcon(null);
      }
    }
    return status;
  }

  private @NotNull RunCurrentFileActionStatus getRunCurrentFileActionStatus(@NotNull AnActionEvent e,
                                                                                                 boolean resetCache) {
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

  private @NotNull RunCurrentFileActionStatus getRunCurrentFileActionStatus(@NotNull PsiFile psiFile,
                                                                                                 boolean resetCache,
                                                                                                 @NotNull AnActionEvent e) {
    List<RunnerAndConfigurationSettings> runConfigs = getRunConfigsForCurrentFile(psiFile, resetCache);
    if (runConfigs.isEmpty()) {
      String tooltip = ExecutionBundle.message("run.button.on.toolbar.tooltip.current.file.not.runnable");
      return RunCurrentFileActionStatus.createDisabled(tooltip, myExecutor.getIcon());
    }

    List<RunnerAndConfigurationSettings> runnableConfigs = filterConfigsThatHaveRunner(runConfigs);
    if (runnableConfigs.isEmpty()) {
      return RunCurrentFileActionStatus.createDisabled(myExecutor.getStartActionText(psiFile.getName()),
                                                                            myExecutor.getIcon());
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

    return RunCurrentFileActionStatus.createEnabled(myExecutor.getStartActionText(psiFile.getName()), icon,
                                                                         runnableConfigs);
  }

  @Override
  public String getId() {
    return myExecutor.getId();
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
    runningDescriptors =
      ContainerUtil.filter(runningDescriptors, descriptor -> executionManager.getExecutors(descriptor).contains(myExecutor));
    return runningDescriptors;
  }

  protected @Nullable RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunManager runManager = project == null ? null : RunManager.getInstanceIfCreated(project);
    return runManager == null ? null : runManager.getSelectedConfiguration();
  }

  protected void run(@NotNull Project project, @NotNull RunnerAndConfigurationSettings settings, @NotNull DataContext dataContext) {
    ExecutorRegistryImpl.RunnerHelper.run(project, settings.getConfiguration(), settings, dataContext, myExecutor);
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

  @Override
  public @NotNull Collection<@NotNull Permission> getRequiredPermissions() {
    return List.of(getFullRunAccess());
  }

  private record RunCurrentFileInfo(
    long myPsiModCount,
    @NotNull List<RunnerAndConfigurationSettings> myRunConfigs) {
  }

  private record RunCurrentFileActionStatus(
    boolean enabled,
    @Nls @NotNull String tooltip,
    @NotNull Icon icon,
    @NotNull List<RunnerAndConfigurationSettings> myRunConfigs) {

    static @NotNull RunCurrentFileActionStatus createDisabled(@Nls @NotNull String tooltip,
                                                              @NotNull Icon icon) {
      return new RunCurrentFileActionStatus(false, tooltip, icon, emptyList());
    }

    static @NotNull RunCurrentFileActionStatus createEnabled(@Nls @NotNull String tooltip,
                                                             @NotNull Icon icon,
                                                             @NotNull List<RunnerAndConfigurationSettings> runConfigs) {
      return new RunCurrentFileActionStatus(true, tooltip, icon, runConfigs);
    }
  }
}

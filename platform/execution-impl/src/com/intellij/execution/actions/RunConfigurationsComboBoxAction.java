// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.codeWithMe.ClientId;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.runToolbar.RunToolbarSlotManager;
import com.intellij.execution.ui.RedesignedRunWidgetKt;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.ActionPopupStep;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.execution.ui.RunToolbarPopupKt.RUN_CONFIGURATION_KEY;

public class RunConfigurationsComboBoxAction extends ComboBoxAction implements DumbAware {
  private static final String BUTTON_MODE = "ButtonMode";
  private static final String RUN_CONFIGURATION_GROUP_ID = "RunConfiguration.Group";

  public static final Icon EMPTY_ICON = EmptyIcon.ICON_16;

  public static boolean hasRunCurrentFileItem(@NotNull Project project) {
    // `RunToolbarSlotManager.getActive$intellij_platform_execution_impl()` is the same as `RunManager.isRiderRunWidgetActive()`
    // but cheaper because it doesn't use RunManager
    if (RunToolbarSlotManager.Companion.getInstance(project).getActive$intellij_platform_execution_impl()) {
      // Run Widget shows up only in Rider. In other IDEs it's a secret feature backed by the "ide.run.widget" Registry key.
      // The 'Run Current File' feature doesn't look great together with the Run Widget.
      return false;
    }

    if (PlatformUtils.isIntelliJ()) return true;
    if (PlatformUtils.isPhpStorm()) return true;
    if (PlatformUtils.isWebStorm()) return true;
    if (PlatformUtils.isRubyMine()) return true;
    if (PlatformUtils.isPyCharmPro()) return true;
    if (PlatformUtils.isPyCharmCommunity()) return true;
    if (PlatformUtils.isDataGrip()) return true;

    return Registry.is("run.current.file.item.in.run.configurations.combobox");
  }

  private static boolean hasRunSubActions(@NotNull Project project) {
    return hasRunCurrentFileItem(project) ||
           ExperimentalUI.isNewUI() ||
           PlatformUtils.isCLion();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      presentation.setDescription(ExecutionBundle.messagePointer("choose.run.configuration.action.description"));
    }
    try {
      if (project == null || project.isDisposed() || !project.isOpen() || RunManager.IS_RUN_MANAGER_INITIALIZED.get(project) != Boolean.TRUE) {
        updatePresentation(null, null, null, presentation, e.getPlace());
        presentation.setEnabled(false);
      }
      else {
        updatePresentation(getSelectedExecutionTarget(e),
                           getSelectedConfiguration(e),
                           project,
                           presentation,
                           e.getPlace());
        presentation.setEnabled(true);
      }
    }
    catch (IndexNotReadyException e1) {
      presentation.setEnabled(false);
    }
  }

  protected @Nullable ExecutionTarget getSelectedExecutionTarget(AnActionEvent e) {
    Project project = e.getProject();
    return project == null ? null : ExecutionTargetManager.getActiveTarget(project);
  }

  protected @Nullable RunnerAndConfigurationSettings getSelectedConfiguration(AnActionEvent e) {
    Project project = e.getProject();
    return project == null ? null : RunManager.getInstance(project).getSelectedConfiguration();
  }

  protected static void updatePresentation(@Nullable ExecutionTarget target,
                                         @Nullable RunnerAndConfigurationSettings settings,
                                         @Nullable Project project,
                                         @NotNull Presentation presentation,
                                         String actionPlace) {
    presentation.putClientProperty(BUTTON_MODE, null);
    if (project != null && target != null && settings != null) {
      String name;
      if (!ExperimentalUI.isNewUI()) { // there's a separate combo-box for execution targets in new UI
        name = Executor.shortenNameIfNeeded(settings.getName());
        if (target != DefaultExecutionTarget.INSTANCE && !target.isExternallyManaged()) {
          name += " | " + target.getDisplayName();
        }
        else {
          if (!ExecutionTargetManager.canRun(settings.getConfiguration(), target)) {
            name += " | " + ExecutionBundle.message("run.configurations.combo.action.nothing.to.run.on");
          }
        }
      }
      else {
        name = StringUtil.shortenTextWithEllipsis(
          settings.getName(),
          RedesignedRunWidgetKt.CONFIGURATION_NAME_NON_TRIM_MAX_LENGTH,
          RedesignedRunWidgetKt.CONFIGURATION_NAME_TRIM_SUFFIX_LENGTH,
          true);
      }
      presentation.setText(name, false);
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        setConfigurationIcon(presentation, settings, project, true);
      }
    }
    else {
      if (project != null && hasRunCurrentFileItem(project)) {
        presentation.setText(ExecutionBundle.messagePointer("run.configurations.combo.run.current.file.selected"));
        presentation.setIcon(null);
        return;
      }

      presentation.putClientProperty(BUTTON_MODE, Boolean.TRUE);
      presentation.setText(ExecutionBundle.messagePointer("action.presentation.RunConfigurationsComboBoxAction.text"));
      presentation.setDescription(ActionsBundle.actionDescription(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS));
      if (ActionPlaces.TOUCHBAR_GENERAL.equals(actionPlace))
        presentation.setIcon(AllIcons.General.Add);
      else
        presentation.setIcon(null);
    }
  }

  protected static void setConfigurationIcon(final Presentation presentation,
                                             final RunnerAndConfigurationSettings settings,
                                             final Project project) {
    setConfigurationIcon(presentation, settings, project, true);
  }

  private static void setConfigurationIcon(final Presentation presentation,
                                           final RunnerAndConfigurationSettings settings,
                                           final Project project,
                                           final boolean withLiveIndicator) {
    try {
      presentation.setIcon(RunManagerEx.getInstanceEx(project).getConfigurationIcon(settings, withLiveIndicator));
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (ActionPlaces.TOUCHBAR_GENERAL.equals(e.getPlace())) {
      final Presentation presentation = e.getPresentation();
      if (Boolean.TRUE.equals(presentation.getClientProperty(BUTTON_MODE))) {
        InputEvent inputEvent = e.getInputEvent();
        Component component = inputEvent != null ? inputEvent.getComponent() : null;
        if (component != null) {
          performWhenButton(component, ActionPlaces.TOUCHBAR_GENERAL);
        }
        return;
      }
    }
    super.actionPerformed(e);
  }

  @Override
  protected boolean shouldShowDisabledActions() {
    return true;
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    ComboBoxButton button = new RunConfigurationsComboBoxButton(presentation);
    if (isNoWrapping(place)) return button;

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
    Border border = UIUtil.isUnderDefaultMacTheme() ?
                    JBUI.Borders.empty(0, 2) : JBUI.Borders.empty(0, 5, 0, 4);

    panel.setBorder(border);
    panel.add(button);
    return panel;
  }

  private static void performWhenButton(@NotNull Component src, String place) {
    ActionManager manager = ActionManager.getInstance();
    manager.tryToExecute(manager.getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS),
      new MouseEvent(src, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 0, 0, 0,false, 0),
      src, place, true
    );
  }

  @Override
  protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    DefaultActionGroup result = new DefaultActionGroup();
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(button));
    if (project == null) {
      return result;
    }

    AnAction editRunConfigurationAction = getEditRunConfigurationAction();
    if (editRunConfigurationAction != null) {
      result.add(editRunConfigurationAction);
    }
    result.add(new SaveTemporaryAction());
    result.addSeparator();


    if (!ExperimentalUI.isNewUI()) {
      // no need for the target list in `All configurations` in new UI, since there is a separate combobox for them
      addTargetGroup(project, result);
    }

    result.add(new RunCurrentFileAction());
    result.addSeparator(ExecutionBundle.message("run.configurations.popup.existing.configurations.separator.text"));

    Map<ConfigurationType, Map<String, List<RunnerAndConfigurationSettings>>> configurationMap =
      RunManagerImpl.getInstanceImpl(project).getConfigurationsGroupedByTypeAndFolder(true);
    result.add(createRunConfigurationFolderActions(project, configurationMap.values()));
    return result;
  }

  private @NotNull AnAction createRunConfigurationFolderActions(
    @NotNull Project project,
    @NotNull Collection<Map<String, List<RunnerAndConfigurationSettings>>> folderMaps) {
    return new ActionGroup() {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        List<AnAction> result = new ArrayList<>();
        for (Map<String, List<RunnerAndConfigurationSettings>> folderMap : folderMaps) {
          result.add(new ActionGroup() {
            @Override
            public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
              List<AnAction> result = new ArrayList<>();
              for (Map.Entry<String, List<RunnerAndConfigurationSettings>> folderEntry : folderMap.entrySet()) {
                @NlsSafe String folderName = folderEntry.getKey();
                if (folderName == null) {
                  result.addAll(ContainerUtil.map(folderEntry.getValue(), o -> createFinalAction(project, o)));
                }
                else {
                  result.add(new ActionGroup() {
                    {
                      getTemplatePresentation().setPopupGroup(true);
                      getTemplatePresentation().setText(folderName);
                      getTemplatePresentation().setIcon(AllIcons.Nodes.Folder);
                    }

                    @Override
                    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
                      return ContainerUtil.map(folderEntry.getValue(),
                                               o -> createFinalAction(project, o)).toArray(AnAction.EMPTY_ARRAY);
                    }
                  });
                }
              }
              return result.toArray(AnAction.EMPTY_ARRAY);
            }
          });
          result.add(Separator.getInstance());
        }
        return result.toArray(AnAction.EMPTY_ARRAY);
      }
    };
  }

  protected void addTargetGroup(Project project, DefaultActionGroup allActionsGroup) {
    RunnerAndConfigurationSettings selected = RunManager.getInstance(project).getSelectedConfiguration();
    if (selected != null) {
      ExecutionTarget activeTarget = ExecutionTargetManager.getActiveTarget(project);
      for (ExecutionTarget eachTarget : ExecutionTargetManager.getTargetsToChooseFor(project, selected.getConfiguration())) {
        allActionsGroup.add(new SelectTargetAction(project, eachTarget, eachTarget.equals(activeTarget)));
      }
      allActionsGroup.addSeparator();
    }
  }

  protected @Nullable AnAction getEditRunConfigurationAction() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS);
  }

  protected @NotNull AnAction createFinalAction(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
    return new SelectConfigAction(project, configuration);
  }

  public final class RunConfigurationsComboBoxButton extends ComboBoxButton {

    public RunConfigurationsComboBoxButton(@NotNull Presentation presentation) {
      super(presentation);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension d = super.getPreferredSize();
      d.width = Math.max(d.width, JBUIScale.scale(75));
      return d;
    }

    @Override
    protected void doShiftClick() {
      DataContext context = DataManager.getInstance().getDataContext(this);
      final Project project = CommonDataKeys.PROJECT.getData(context);
      if (project != null && !ActionUtil.isDumbMode(project)) {
        new EditConfigurationsDialog(project).show();
        return;
      }
      super.doShiftClick();
    }

    @Override
    protected void fireActionPerformed(ActionEvent event) {
      if (Boolean.TRUE.equals(getPresentation().getClientProperty(BUTTON_MODE))) {
        performWhenButton(this, ActionPlaces.UNKNOWN);
        return;
      }

      super.fireActionPerformed(event);
    }

    @Override
    protected boolean isArrowVisible(@NotNull Presentation presentation) {
      return !Boolean.TRUE.equals(presentation.getClientProperty(BUTTON_MODE));
    }
  }

  @Override
  protected JBPopup createActionPopup(DefaultActionGroup group,
                                      @NotNull DataContext context,
                                      @Nullable Runnable disposeCallback) {
    JBPopup popup = super.createActionPopup(group, context, disposeCallback);
    if (popup instanceof PopupFactoryImpl.ActionGroupPopup actionGroupPopup) {
      PopupStep<?> step = actionGroupPopup.getStep();
      if (step instanceof ActionPopupStep actionPopupStep) {
        actionPopupStep.setSubStepContextAdjuster((stepContext, action) -> {
          if (action instanceof SelectConfigAction selectConfigAction) {
            return CustomizedDataContext.withSnapshot(stepContext, sink -> {
              sink.set(RUN_CONFIGURATION_KEY, selectConfigAction.getConfiguration());
            });
          }
          else {
            return stepContext;
          }
        });
      }
    }
    return popup;
  }

  private static final class SaveTemporaryAction extends DumbAwareAction {
    SaveTemporaryAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.Actions.MenuSaveall);
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project != null) {
        RunnerAndConfigurationSettings settings = chooseTempSettings(project);
        if (settings != null) {
          final RunManager runManager = RunManager.getInstance(project);
          runManager.makeStable(settings);
        }
      }
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project == null) {
        disable(presentation);
        return;
      }
      RunnerAndConfigurationSettings settings = chooseTempSettings(project);
      if (settings == null) {
        disable(presentation);
      }
      else {
        presentation.setText(ExecutionBundle.messagePointer("save.temporary.run.configuration.action.name", Executor.shortenNameIfNeeded(settings.getName())));
        //noinspection DialogTitleCapitalization
        presentation.setDescription(presentation.getText());
        presentation.setEnabledAndVisible(true);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    private static void disable(final Presentation presentation) {
      presentation.setEnabledAndVisible(false);
    }

    private static @Nullable RunnerAndConfigurationSettings chooseTempSettings(@NotNull Project project) {
      RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(project).getSelectedConfiguration();
      if (selectedConfiguration != null && selectedConfiguration.isTemporary()) {
        return selectedConfiguration;
      }
      return ContainerUtil.getFirstItem(RunManager.getInstance(project).getTempConfigurationsList());
    }
  }


  public static void forAllExecutors(@NotNull Consumer<? super Executor> executorProcessor) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      if (executor instanceof ExecutorGroup) {
        for (Executor childExecutor : ((ExecutorGroup<?>)executor).childExecutors()) {
          executorProcessor.accept(childExecutor);
        }
      }
      else {
        executorProcessor.accept(executor);
      }
    }
  }

  @ApiStatus.Internal
  public static class RunCurrentFileAction extends ActionGroup implements DumbAware {

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return getDefaultChildren(null).toArray(AnAction.EMPTY_ARRAY);
    }

    protected @NotNull List<AnAction> getDefaultChildren(@Nullable Predicate<? super Executor> executorFilter) {
      // Add actions similar to com.intellij.execution.actions.ChooseRunConfigurationPopup.ConfigurationActionsStep#buildActions
      List<AnAction> result = new ArrayList<>();
      forAllExecutors(o -> {
        if (executorFilter == null || executorFilter.test(o)) {
          result.add(new RunCurrentFileExecutorAction(o));
        }
      });
      result.add(Separator.getInstance());
      result.add(new EditRunConfigAndRunCurrentFileExecutorAction(DefaultRunExecutor.getRunExecutorInstance()));
      return result;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setPopupGroup(true);
      e.getPresentation().setPerformGroup(true);

      e.getPresentation().setText(ExecutionBundle.messagePointer("run.configurations.combo.run.current.file.item.in.dropdown"));
      e.getPresentation().setDescription(ExecutionBundle.messagePointer("run.configurations.combo.run.current.file.description"));
      e.getPresentation().setEnabledAndVisible(e.getProject() != null && hasRunCurrentFileItem(e.getProject()));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;

      RunManager.getInstance(project).setSelectedConfiguration(null);
      updatePresentation(null, null, project, e.getPresentation(), e.getPlace());
    }
  }


  private static final class SelectTargetAction extends AnAction {
    private final Project myProject;
    private final ExecutionTarget myTarget;

    private static final Icon CHECKED_ICON = JBUIScale.scaleIcon(new SizedIcon(AllIcons.Actions.Checked, 16, 16));
    private static final Icon CHECKED_SELECTED_ICON = JBUIScale.scaleIcon(new SizedIcon(AllIcons.Actions.Checked_selected, 16, 16));

    SelectTargetAction(final Project project, final ExecutionTarget target, boolean selected) {
      myProject = project;
      myTarget = target;

      String name = target.getDisplayName();
      Presentation presentation = getTemplatePresentation();
      presentation.setText(name, false);
      presentation.setDescription(ExecutionBundle.message("select.0", name));

      presentation.setIcon(selected ? CHECKED_ICON : EMPTY_ICON);
      presentation.setSelectedIcon(selected ? CHECKED_SELECTED_ICON : EMPTY_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ExecutionTargetManager.setActiveTarget(myProject, myTarget);
      updatePresentation(ExecutionTargetManager.getActiveTarget(myProject),
                         RunManager.getInstance(myProject).getSelectedConfiguration(),
                         myProject,
                         e.getPresentation(),
                         e.getPlace());
    }

    @Override
    public boolean isDumbAware() {
      RunnerAndConfigurationSettings configuration = RunManager.getInstance(myProject).getSelectedConfiguration();
      return configuration == null || configuration.getType().isDumbAware();
    }
  }

  @ApiStatus.Internal
  public static class SelectConfigAction extends ActionGroup implements DumbAware {
    private final Project myProject;
    private final RunnerAndConfigurationSettings myConfiguration;

    public SelectConfigAction(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
      myProject = project;
      myConfiguration = configuration;
      // TODO remove when BackendAsyncActionHost.isNewActionUpdateEnabled is inlined
      if (ClientId.getCurrentOrNull() != null && !Registry.is("rdct.new.async.actions", true)) {
        Presentation p = getTemplatePresentation().clone();
        AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, p, DataContext.EMPTY_CONTEXT);
        Utils.initUpdateSession(event);
        update(event);
        getTemplatePresentation().copyFrom(p, null, true);
      }
    }

    public @NotNull RunnerAndConfigurationSettings getConfiguration() {
      return myConfiguration;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return getDefaultChildren(null).toArray(AnAction.EMPTY_ARRAY);
    }

    protected @NotNull List<AnAction> getDefaultChildren(@Nullable Predicate<? super Executor> executorFilter) {
      // The secondary menu for the existing run configurations is not directly related to the 'Run Current File' feature.
      // We may reconsider changing this to `if (!RunManager.getInstance(project).isRunWidgetActive()) { addSubActions(); }`
      if (!hasRunSubActions(myProject)) return Collections.emptyList();

      List<AnAction> result = new ArrayList<>();
      // Add actions similar to com.intellij.execution.actions.ChooseRunConfigurationPopup.ConfigurationActionsStep#buildActions
      forAllExecutors(o -> {
        if (executorFilter == null || executorFilter.test(o)) {
          result.add(new RunSpecifiedConfigExecutorAction(o, myConfiguration, false));
        }
      });
      result.add(Separator.create(ExperimentalUI.isNewUI() ? ExecutionBundle.message("choose.run.popup.separator") : null));

      if (!ExperimentalUI.isNewUI()) {
        Executor runExecutor = DefaultRunExecutor.getRunExecutorInstance();
        result.add(new RunSpecifiedConfigExecutorAction(runExecutor, myConfiguration, true));
      }
      else {
        // TODO - when the Old UI is removed from the platform at all, do the following:
        //   1) remove this line
        //   2) include the action with id `IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS`
        //      into the action group with id `RunConfigurationsComboBoxAction.RUN_CONFIGURATION_GROUP_ID`
        //      in xml-file where the action group is declared
        result.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS));
      }

      result.add(ActionManager.getInstance().getAction(RUN_CONFIGURATION_GROUP_ID));

      return result;
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      RunManager.getInstance(myProject).setSelectedConfiguration(myConfiguration);
      updatePresentation(ExecutionTargetManager.getActiveTarget(myProject), myConfiguration, myProject, e.getPresentation(), e.getPlace());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setPopupGroup(true);
      e.getPresentation().setPerformGroup(true);
      e.getPresentation().putClientProperty(ActionUtil.ALWAYS_VISIBLE_GROUP, true);

      String fullName = myConfiguration.getName();
      String name = StringUtil.notNullize(StringUtil.nullize(Executor.shortenNameIfNeeded(fullName), " "));

      String toolTip = name.equals(fullName) ? null : fullName;
      Presentation presentation = e.getPresentation();
      presentation.setText(name, false);
      presentation.setDescription(ExecutionBundle.message("select.0.1", myConfiguration.getType().getConfigurationTypeDescription(), name));
      presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, toolTip);

      setConfigurationIcon(e.getPresentation(), myConfiguration, myProject);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public class RunConfigurationsComboBoxAction extends ComboBoxAction implements DumbAware {
  private static final String BUTTON_MODE = "ButtonMode";

  public static final Icon CHECKED_ICON = JBUIScale.scaleIcon(new SizedIcon(AllIcons.Actions.Checked, 16, 16));
  public static final Icon CHECKED_SELECTED_ICON = JBUIScale.scaleIcon(new SizedIcon(AllIcons.Actions.Checked_selected, 16, 16));
  public static final Icon EMPTY_ICON = EmptyIcon.ICON_16;

  public static boolean hasRunCurrentFileItem(@NotNull Project project) {
    if (RunManager.getInstance(project).isRunWidgetActive()) {
      // Run Widget shows up only in Rider. In other IDEs it's a secret feature backed by the "ide.run.widget" Registry key.
      // The 'Run Current File' feature doesn't look great together with the Run Widget.
      return false;
    }

    return Registry.is("run.current.file.item.in.run.configurations.combobox");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      presentation.setDescription(ExecutionBundle.messagePointer("choose.run.configuration.action.description"));
    }
    try {
      if (project == null || project.isDisposed() || !project.isOpen()) {
        updatePresentation(null, null, null, presentation, e.getPlace());
        presentation.setEnabled(false);
      }
      else {
        updatePresentation(ExecutionTargetManager.getActiveTarget(project),
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
      String name = Executor.shortenNameIfNeeded(settings.getName());
      if (target != DefaultExecutionTarget.INSTANCE && !target.isExternallyManaged()) {
        name += " | " + target.getDisplayName();
      } else {
        if (!ExecutionTargetManager.canRun(settings.getConfiguration(), target)) {
          name += " | " + ExecutionBundle.message("run.configurations.combo.action.nothing.to.run.on");
        }
      }
      presentation.setText(name, false);
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        setConfigurationIcon(presentation, settings, project);
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
    try {
      presentation.setIcon(RunManagerEx.getInstanceEx(project).getConfigurationIcon(settings, true));
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

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull final Presentation presentation, @NotNull String place) {
    ComboBoxButton button = new RunConfigurationsComboBoxButton(presentation);
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
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
    final DefaultActionGroup allActionsGroup = new DefaultActionGroup();
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(button));
    if (project == null) {
      return allActionsGroup;
    }

    AnAction editRunConfigurationAction = getEditRunConfigurationAction();
    if(editRunConfigurationAction != null) {
      allActionsGroup.add(editRunConfigurationAction);
    }
    allActionsGroup.add(new SaveTemporaryAction());
    allActionsGroup.addSeparator();

    RunnerAndConfigurationSettings selected = RunManager.getInstance(project).getSelectedConfiguration();
    if (selected != null) {
      ExecutionTarget activeTarget = ExecutionTargetManager.getActiveTarget(project);
      for (ExecutionTarget eachTarget : ExecutionTargetManager.getTargetsToChooseFor(project, selected.getConfiguration())) {
        allActionsGroup.add(new SelectTargetAction(project, eachTarget, eachTarget.equals(activeTarget)));
      }
      allActionsGroup.addSeparator();
    }

    allActionsGroup.add(new RunCurrentFileAction());
    allActionsGroup.addSeparator(ExecutionBundle.message("run.configurations.popup.existing.configurations.separator.text"));

    for (Map<String, List<RunnerAndConfigurationSettings>> structure : RunManagerImpl.getInstanceImpl(project).getConfigurationsGroupedByTypeAndFolder(true).values()) {
      final DefaultActionGroup actionGroup = new DefaultActionGroup();
      for (Map.Entry<String, List<RunnerAndConfigurationSettings>> entry : structure.entrySet()) {
        @NlsSafe String folderName = entry.getKey();
        DefaultActionGroup group = folderName == null ? actionGroup : DefaultActionGroup.createPopupGroup(() -> folderName);
        group.getTemplatePresentation().setIcon(AllIcons.Nodes.Folder);
        for (RunnerAndConfigurationSettings settings : entry.getValue()) {
          group.add(createFinalAction(settings, project));
        }
        if (group != actionGroup) {
          actionGroup.add(group);
        }
      }

      allActionsGroup.add(actionGroup);
      allActionsGroup.addSeparator();
    }
    return allActionsGroup;
  }

  protected @Nullable AnAction getEditRunConfigurationAction() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS);
  }

  protected AnAction createFinalAction(@NotNull final RunnerAndConfigurationSettings configuration, @NotNull final Project project) {
    return new SelectConfigAction(configuration, project);
  }

  public class RunConfigurationsComboBoxButton extends ComboBoxButton {

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



  private static final class SaveTemporaryAction extends DumbAwareAction {
    SaveTemporaryAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.Actions.MenuSaveall);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
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
    public void update(@NotNull final AnActionEvent e) {
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

    private static void disable(final Presentation presentation) {
      presentation.setEnabledAndVisible(false);
    }

    @Nullable
    private static RunnerAndConfigurationSettings chooseTempSettings(@NotNull Project project) {
      RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(project).getSelectedConfiguration();
      if (selectedConfiguration != null && selectedConfiguration.isTemporary()) {
        return selectedConfiguration;
      }
      return ContainerUtil.getFirstItem(RunManager.getInstance(project).getTempConfigurationsList());
    }
  }


  private static class RunCurrentFileAction extends AnAction {
    private RunCurrentFileAction() {
      super(ExecutionBundle.messagePointer("run.configurations.combo.run.current.file.item.in.dropdown"),
            ExecutionBundle.messagePointer("run.configurations.combo.run.current.file.description"),
            null);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null && hasRunCurrentFileItem(e.getProject()));
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

  private static final class SelectConfigAction extends DumbAwareAction {
    private final RunnerAndConfigurationSettings myConfiguration;
    private final Project myProject;

    SelectConfigAction(final RunnerAndConfigurationSettings configuration, final Project project) {
      myConfiguration = configuration;
      myProject = project;
      String name = Executor.shortenNameIfNeeded(configuration.getName());
      if (name.isEmpty()) {
        name = " ";
      }
      final Presentation presentation = getTemplatePresentation();
      presentation.setText(name, false);
      presentation.setDescription(ExecutionBundle.message("select.0.1", configuration.getType().getConfigurationTypeDescription(), name));
      updateIcon(presentation);
    }

    private void updateIcon(final Presentation presentation) {
      setConfigurationIcon(presentation, myConfiguration, myProject);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      RunManager.getInstance(myProject).setSelectedConfiguration(myConfiguration);
      updatePresentation(ExecutionTargetManager.getActiveTarget(myProject), myConfiguration, myProject, e.getPresentation(), e.getPlace());
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      updateIcon(e.getPresentation());
    }
  }
}

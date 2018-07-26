// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.IconUtil;
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
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public class RunConfigurationsComboBoxAction extends ComboBoxAction implements DumbAware {
  private static final String BUTTON_MODE = "ButtonMode";

  public static final Icon CHECKED_ICON = JBUI.scale(new SizedIcon(AllIcons.Actions.Checked, 16, 16));
  public static final Icon CHECKED_SELECTED_ICON = JBUI.scale(new SizedIcon(AllIcons.Actions.Checked_selected, 16, 16));
  public static final Icon EMPTY_ICON = EmptyIcon.ICON_16;

  private ComboBoxButton myButton;

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      presentation.setDescription(ExecutionBundle.message("choose.run.configuration.action.description"));
    }
    try {
      if (project == null || project.isDisposed() || !project.isOpen()) {
        updatePresentation(null, null, null, presentation, e.getPlace());
        presentation.setEnabled(false);
      }
      else {
        updatePresentation(ExecutionTargetManager.getActiveTarget(project),
                           RunManager.getInstance(project).getSelectedConfiguration(),
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

  private static void updatePresentation(@Nullable ExecutionTarget target,
                                         @Nullable RunnerAndConfigurationSettings settings,
                                         @Nullable Project project,
                                         @NotNull Presentation presentation,
                                         String actionPlace) {
    presentation.putClientProperty(BUTTON_MODE, null);
    if (project != null && target != null && settings != null) {
      String name = Executor.shortenNameIfNeed(settings.getName());
      if (target != DefaultExecutionTarget.INSTANCE) {
        name += " | " + target.getDisplayName();
      } else {
        if (!ExecutionTargetManager.canRun(settings, target)) {
          name += " | Nothing to run on";
        }
      }
      presentation.setText(name, false);
      setConfigurationIcon(presentation, settings, project);
    }
    else {
      presentation.putClientProperty(BUTTON_MODE, Boolean.TRUE);
      presentation.setText("Add Configuration...");
      presentation.setDescription(ActionsBundle.actionDescription(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS));
      if (ActionPlaces.TOUCHBAR_GENERAL.equals(actionPlace))
        presentation.setIcon(AllIcons.General.Add);
      else
        presentation.setIcon(null);
    }
  }

  private static void setConfigurationIcon(final Presentation presentation,
                                           final RunnerAndConfigurationSettings settings,
                                           final Project project) {
    try {
      Icon icon = RunManagerEx.getInstanceEx(project).getConfigurationIcon(settings);
      ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(project);
      List<RunContentDescriptor> runningDescriptors = executionManager.getRunningDescriptors(s -> s == settings);
      if (runningDescriptors.size() == 1) {
        icon = ExecutionUtil.getLiveIndicator(icon);
      }
      if (runningDescriptors.size() > 1) {
        icon = IconUtil.addText(icon, String.valueOf(runningDescriptors.size()));
      }
      presentation.setIcon(icon);
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (ActionPlaces.TOUCHBAR_GENERAL.equals(e.getPlace())) {
      final Presentation presentation = e.getPresentation();
      if (Boolean.TRUE.equals(presentation.getClientProperty(BUTTON_MODE))) {
        performWhenButton(myButton, ActionPlaces.TOUCHBAR_GENERAL);
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
  public JComponent createCustomComponent(final Presentation presentation) {
    myButton = new ComboBoxButton(presentation) {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.width = Math.max(d.width, JBUI.scale(75));
        return d;
      }

      @Override
      protected void fireActionPerformed(ActionEvent event) {
        if (Boolean.TRUE.equals(presentation.getClientProperty(BUTTON_MODE))) {
          performWhenButton(this, ActionPlaces.UNKNOWN);
          return;
        }

        super.fireActionPerformed(event);
      }

      @Override
      protected boolean isArrowVisible(@NotNull Presentation presentation) {
        return !Boolean.TRUE.equals(presentation.getClientProperty(BUTTON_MODE));
      }
    };
    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
    Border border = UIUtil.isUnderDefaultMacTheme() ?
                    JBUI.Borders.empty(0, 2) : JBUI.Borders.empty(0, 5, 0, 4);

    panel.setBorder(border);
    panel.add(myButton);
    return panel;
  }

  private void performWhenButton(@NotNull Component src, String place) {
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
    if (project != null) {

      allActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS));
      allActionsGroup.add(new SaveTemporaryAction());
      allActionsGroup.addSeparator();

      RunnerAndConfigurationSettings selected = RunManager.getInstance(project).getSelectedConfiguration();
      if (selected != null) {
        ExecutionTarget activeTarget = ExecutionTargetManager.getActiveTarget(project);
        for (ExecutionTarget eachTarget : ExecutionTargetManager.getTargetsToChooseFor(project, selected)) {
          allActionsGroup.add(new SelectTargetAction(project, eachTarget, eachTarget.equals(activeTarget)));
        }
        allActionsGroup.addSeparator();
      }

      final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
      for (ConfigurationType type : runManager.getConfigurationFactories()) {
        final DefaultActionGroup actionGroup = new DefaultActionGroup();
        Map<String,List<RunnerAndConfigurationSettings>> structure = runManager.getStructure(type);
        for (Map.Entry<String, List<RunnerAndConfigurationSettings>> entry : structure.entrySet()) {
          DefaultActionGroup group = entry.getKey() != null ? new DefaultActionGroup(entry.getKey(), true) : actionGroup;
          group.getTemplatePresentation().setIcon(AllIcons.Nodes.Folder);
          for (RunnerAndConfigurationSettings settings : entry.getValue()) {
            group.add(new SelectConfigAction(settings, project));
          }
          if (group != actionGroup) {
            actionGroup.add(group);
          }
        }

        allActionsGroup.add(actionGroup);
        allActionsGroup.addSeparator();
      }
    }
    return allActionsGroup;
  }

  private static class SaveTemporaryAction extends DumbAwareAction {

    public SaveTemporaryAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.RunConfigurations.SaveTempConfig);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
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
    public void update(final AnActionEvent e) {
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
        presentation.setText(ExecutionBundle.message("save.temporary.run.configuration.action.name", Executor.shortenNameIfNeed(settings.getName())));
        presentation.setDescription(presentation.getText());
        presentation.setVisible(true);
        presentation.setEnabled(true);
      }
    }

    private static void disable(final Presentation presentation) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
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

  private static class SelectTargetAction extends AnAction {
    private final Project myProject;
    private final ExecutionTarget myTarget;

    public SelectTargetAction(final Project project, final ExecutionTarget target, boolean selected) {
      myProject = project;
      myTarget = target;

      String name = target.getDisplayName();
      Presentation presentation = getTemplatePresentation();
      presentation.setText(name, false);
      presentation.setDescription("Select " + name);

      presentation.setIcon(selected ? CHECKED_ICON : EMPTY_ICON);
      presentation.setSelectedIcon(selected ? CHECKED_SELECTED_ICON : EMPTY_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
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

  private static class SelectConfigAction extends DumbAwareAction {
    private final RunnerAndConfigurationSettings myConfiguration;
    private final Project myProject;

    public SelectConfigAction(final RunnerAndConfigurationSettings configuration, final Project project) {
      myConfiguration = configuration;
      myProject = project;
      String name = Executor.shortenNameIfNeed(configuration.getName());
      if (name.isEmpty()) {
        name = " ";
      }
      final Presentation presentation = getTemplatePresentation();
      presentation.setText(name, false);
      presentation.setDescription("Select " + configuration.getType().getConfigurationTypeDescription() + " '" + name + "'");
      updateIcon(presentation);
    }

    private void updateIcon(final Presentation presentation) {
      setConfigurationIcon(presentation, myConfiguration, myProject);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      RunManager.getInstance(myProject).setSelectedConfiguration(myConfiguration);
      updatePresentation(ExecutionTargetManager.getActiveTarget(myProject), myConfiguration, myProject, e.getPresentation(), e.getPlace());
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      updateIcon(e.getPresentation());
    }
  }
}

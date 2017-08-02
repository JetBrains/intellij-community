/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class RunConfigurationsComboBoxAction extends ComboBoxAction implements DumbAware {

  public static final Icon CHECKED_ICON = JBUI.scale(new SizedIcon(AllIcons.Actions.Checked, 16, 16));
  public static final Icon CHECKED_SELECTED_ICON = JBUI.scale(new SizedIcon(AllIcons.Actions.Checked_selected, 16, 16));
  public static final Icon EMPTY_ICON = EmptyIcon.ICON_16;

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      presentation.setDescription(ExecutionBundle.message("choose.run.configuration.action.description"));
    }
    try {
      if (project == null || project.isDisposed() || !project.isOpen()) {
        updatePresentation(null, null, null, presentation);
        presentation.setEnabled(false);
      }
      else {
        updatePresentation(ExecutionTargetManager.getActiveTarget(project),
                           RunManager.getInstance(project).getSelectedConfiguration(),
                           project,
                           presentation);
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
                                         @NotNull Presentation presentation) {
    if (project != null && target != null && settings != null) {
      String name = Executor.shortenNameIfNeed(settings.getName());
      if (target != DefaultExecutionTarget.INSTANCE) {
        name += " | " + target.getDisplayName();
      } else {
        if (!settings.canRunOn(target)) {
          name += " | Nothing to run on";
        }
      }
      presentation.setText(name, false);
      setConfigurationIcon(presentation, settings, project);
    }
    else {
      presentation.setText(""); // IDEA-21657
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
  protected boolean shouldShowDisabledActions() {
    return true;
  }

  @Override
  public JComponent createCustomComponent(final Presentation presentation) {
    ComboBoxButton button = createComboBoxButton(presentation);
    button.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(0, 0, 0, 2));
    panel.add(button);
    return panel;
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
                         e.getPresentation());
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
      updatePresentation(ExecutionTargetManager.getActiveTarget(myProject), myConfiguration, myProject, e.getPresentation());
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      updateIcon(e.getPresentation());
    }
  }
}

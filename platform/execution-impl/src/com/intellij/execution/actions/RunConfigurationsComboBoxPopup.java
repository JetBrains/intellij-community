// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * Builds the button and the popup of {@link RunConfigurationsComboBoxAction}.
 * The action class does not refer to the popup classes, so the JVM does not load them when it creates the action.
 */
@SuppressWarnings("UnnecessarilyQualifiedStaticUsage")
final class RunConfigurationsComboBoxPopup {
  private RunConfigurationsComboBoxPopup() {
  }

  static @NotNull JComponent createButton(@NotNull RunConfigurationsComboBoxAction action, @NotNull Presentation presentation) {
    return action.new RunConfigurationsComboBoxButton(presentation);
  }

  static @NotNull DefaultActionGroup createPopupActionGroup(@NotNull RunConfigurationsComboBoxAction action, @NotNull JComponent button) {
    DefaultActionGroup result = new DefaultActionGroup();
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(button));
    if (project == null) {
      return result;
    }

    AnAction editRunConfigurationAction = action.getEditRunConfigurationAction();
    if (editRunConfigurationAction != null) {
      result.add(editRunConfigurationAction);
    }
    result.add(new SaveTemporaryAction());
    result.addSeparator();


    if (!ExperimentalUI.isNewUI()) {
      // no need for the target list in `All configurations` in new UI, since there is a separate combobox for them
      action.addTargetGroup(project, result);
    }

    result.add(new RunConfigurationsComboBoxAction.RunCurrentFileAction());
    result.addSeparator(ExecutionBundle.message("run.configurations.popup.existing.configurations.separator.text"));

    Map<ConfigurationType, Map<String, List<RunnerAndConfigurationSettings>>> configurationMap =
      RunManagerImpl.getInstanceImpl(project).getConfigurationsGroupedByTypeAndFolder(true);
    result.add(createRunConfigurationFolderActions(action, project, configurationMap.values()));
    return result;
  }

  private static @NotNull AnAction createRunConfigurationFolderActions(
    @NotNull RunConfigurationsComboBoxAction action,
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
                  result.addAll(ContainerUtil.map(folderEntry.getValue(), o -> action.createFinalAction(project, o)));
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
                                               o -> action.createFinalAction(project, o)).toArray(AnAction.EMPTY_ARRAY);
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

  static void addTargetGroup(Project project, DefaultActionGroup allActionsGroup) {
    RunnerAndConfigurationSettings selected = RunManager.getInstance(project).getSelectedConfiguration();
    if (selected != null) {
      ExecutionTarget activeTarget = ExecutionTargetManager.getActiveTarget(project);
      for (ExecutionTarget eachTarget : ExecutionTargetManager.getTargetsToChooseFor(project, selected.getConfiguration())) {
        allActionsGroup.add(new SelectTargetAction(project, eachTarget, eachTarget.equals(activeTarget)));
      }
      allActionsGroup.addSeparator();
    }
  }

  static @NotNull AnAction createSelectConfigAction(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
    return new RunConfigurationsComboBoxAction.SelectConfigAction(project, configuration);
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

      presentation.setIcon(selected ? CHECKED_ICON : RunConfigurationsComboBoxAction.EMPTY_ICON);
      presentation.setSelectedIcon(selected ? CHECKED_SELECTED_ICON : RunConfigurationsComboBoxAction.EMPTY_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ExecutionTargetManager.setActiveTarget(myProject, myTarget);
      RunConfigurationsComboBoxAction.updatePresentation(ExecutionTargetManager.getActiveTarget(myProject),
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
}

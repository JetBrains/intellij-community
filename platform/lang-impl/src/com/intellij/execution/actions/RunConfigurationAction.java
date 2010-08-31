/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunConfigurationAction extends ComboBoxAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.RunConfigurationAction");
  private static final Key<ComboBoxButton> BUTTON_KEY = Key.create("COMBOBOX_BUTTON");

  public void actionPerformed(final AnActionEvent e) {
    final IdeFrameImpl ideFrame = findFrame(e.getData(PlatformDataKeys.CONTEXT_COMPONENT));
    final ComboBoxButton button = (ComboBoxButton)ideFrame.getRootPane().getClientProperty(BUTTON_KEY);
    if (button == null || !button.isShowing()) return;
    button.showPopup();
  }

  private static IdeFrameImpl findFrame(final Component component) {
    return IJSwingUtilities.findParentOfType(component, IdeFrameImpl.class);
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      presentation.setDescription(ExecutionBundle.message("choose.run.configuration.action.description"));
      presentation.setEnabled(findFrame(e.getData(PlatformDataKeys.CONTEXT_COMPONENT)) != null);
      return;
    }

    try {
      if (project == null || project.isDisposed() || !project.isInitialized()) {
        //if (ProjectManager.getInstance().getOpenProjects().length > 0) {
        //  // do nothing if frame is not active
        //  return;
        //}

        updateButton(null, null, presentation);
        presentation.setEnabled(false);
      }
      else {
        if (DumbService.getInstance(project).isDumb()) {
          presentation.setEnabled(false);
        }
        else {
          final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
          RunnerAndConfigurationSettings selected = runManager.getSelectedConfiguration();
          updateButton(selected, project, presentation);
          presentation.setEnabled(true);
        }
      }
    }
    catch (IndexNotReadyException e1) {
      presentation.setEnabled(false);
    }
  }

  private static void updateButton(final @Nullable RunnerAndConfigurationSettings settings, final @Nullable Project project,
                                   final @NotNull Presentation presentation) {
    if (project != null && settings != null) {
      presentation.setText(settings.getName(), false);
      setConfigurationIcon(presentation, settings, project);
    }
    else {
      presentation.setText(""); // IDEA-21657
      presentation.setIcon(null);
    }
  }

  private static void setConfigurationIcon(final Presentation presentation, final RunnerAndConfigurationSettings settings, final Project project) {
    try {
      presentation.setIcon(ProgramRunnerUtil.getConfigurationIcon(project, settings));
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  public JComponent createCustomComponent(final Presentation presentation) {
    return new ComboBoxButton(presentation) {
      public void addNotify() {
        super.addNotify();    //To change body of overriden methods use Options | File Templates.;
        final IdeFrameImpl frame = findFrame(this);
        LOG.assertTrue(frame != null);
        frame.getRootPane().putClientProperty(BUTTON_KEY, this);
      }
    };
  }


  @NotNull
  protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
    final DefaultActionGroup allActionsGroup = new DefaultActionGroup();
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(button));
    if (project != null) {
      final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);

      allActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS));
      allActionsGroup.add(new SaveTemporaryAction());
      allActionsGroup.addSeparator();

      final ConfigurationType[] types = runManager.getConfigurationFactories();
      for (ConfigurationType type : types) {
        final DefaultActionGroup actionGroup = new DefaultActionGroup();
        final RunnerAndConfigurationSettings[] configurations = runManager.getConfigurationSettings(type);
        for (final RunnerAndConfigurationSettings configuration : configurations) {
          //if (runManager.canRunConfiguration(configuration)) {
          final MenuAction action = new MenuAction(configuration, project);
          actionGroup.add(action);
          //}
        }
        allActionsGroup.add(actionGroup);
        allActionsGroup.addSeparator();
      }
    }
    return allActionsGroup;
  }

  private static class SaveTemporaryAction extends AnAction {
    private static final Icon ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");

    public SaveTemporaryAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final Project project = e.getData(PlatformDataKeys.PROJECT);
      if (project != null) {
        final RunManager runManager = RunManager.getInstance(project);
        runManager.makeStable(chooseTempConfiguration(project));
      }
    }

    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final Project project = e.getData(PlatformDataKeys.PROJECT);
      if (project == null) {
        disable(presentation);
        return;
      }
      RunConfiguration configuration = chooseTempConfiguration(project);
      if (configuration == null) {
        disable(presentation);
      } else {
        presentation.setText(ExecutionBundle.message("save.temporary.run.configuration.action.name", configuration.getName()));
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
    private static RunConfiguration chooseTempConfiguration(Project project) {
      final RunConfiguration[] tempConfigurations = RunManager.getInstance(project).getTempConfigurations();
      if (tempConfigurations.length > 0) {
        RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(project).getSelectedConfiguration();
        if (selectedConfiguration == null || !selectedConfiguration.isTemporary()) {
          return tempConfigurations[0];
        }
        return selectedConfiguration.getConfiguration();
      }
      return null;
    }
  }

  private static class MenuAction extends AnAction {
    private final RunnerAndConfigurationSettings myConfiguration;
    private final Project myProject;

    public MenuAction(final RunnerAndConfigurationSettings configuration, final Project project) {
      myConfiguration = configuration;
      myProject = project;
      String name = configuration.getName();
      if (name == null || name.length() == 0) {
        name = " ";
      }
      final Presentation presentation = getTemplatePresentation();
      presentation.setText(name, false);
      presentation.setDescription("Start "+configuration.getType().getConfigurationTypeDescription()+" '"+name+"'");
      updateIcon(presentation);
    }

    private void updateIcon(final Presentation presentation) {
      setConfigurationIcon(presentation, myConfiguration, myProject);
    }

    public void actionPerformed(final AnActionEvent e){
      RunManagerEx.getInstanceEx(myProject).setActiveConfiguration(myConfiguration);
      updateButton(myConfiguration, myProject, e.getPresentation());
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      updateIcon(e.getPresentation());
    }
  }
}

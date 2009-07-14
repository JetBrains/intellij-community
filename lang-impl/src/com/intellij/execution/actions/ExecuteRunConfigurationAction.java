package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author spleaner
 */
public class ExecuteRunConfigurationAction extends AnAction implements DumbAware {
  private static final Icon EDIT_ICON = IconLoader.getIcon("/actions/editSource.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");

  private Executor myExecutor;
  private static final String EXECUTOR_KEY = "com.intellij.execution.actions.ExecuteRunConfigurationAction.DefauktExecutor";

  public static ExecuteRunConfigurationAction getActionInstance() {
    return (ExecuteRunConfigurationAction)ActionManager.getInstance().getAction("ExecuteRunConfiguration");
  }

  public void setExecutor(@NotNull final Executor executor, @NotNull final Project project) {
    myExecutor = executor;
    final Presentation presentation = getTemplatePresentation();
    presentation.setText(String.format("%s configuration...", executor.getActionName()));
    presentation.setIcon(executor.getIcon());

    final PropertiesComponent properties = PropertiesComponent.getInstance(project);
    properties.setValue(EXECUTOR_KEY, executor.getId());
  }

  public Executor getExecutor() {
    return myExecutor;
  }

  private void initializeDefaultExecutor(@NotNull final Project project) {
    if (myExecutor != null) {
      return;
    }
    
    final PropertiesComponent properties = PropertiesComponent.getInstance(project);
    final ExecutorRegistry registry = ExecutorRegistry.getInstance();
    String id = properties.getValue(EXECUTOR_KEY);
    if (id != null) {
      final Executor executor = registry.getExecutorById(id);
      if (executor != null) {
        setExecutor(executor, project);
        return;
      }
    }

    final Executor runExecutor = DefaultRunExecutor.getRunExecutorInstance();
    if (runExecutor != null) {
      setExecutor(runExecutor, project);
      return;
    }

    final Executor[] registeredExecutors = registry.getRegisteredExecutors();
    if (registeredExecutors.length > 0) {
      setExecutor(registeredExecutors[0], project);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;

    initializeDefaultExecutor(project);

    final ConfigurationListPopupStep popupStep =
      new ConfigurationListPopupStep(project, String.format("%s configuration", getExecutor().getActionName()));
    JBPopupFactory.getInstance().createListPopup(popupStep).showInFocusCenter();
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    presentation.setEnabled(true);
    if (project == null || project.isDisposed()) {
      presentation.setEnabled(false);
      return;
    }

    initializeDefaultExecutor(project);
  }

  private static final class ConfigurationListPopupStep extends BaseListPopupStep<RunnerAndConfigurationSettingsImpl> {
    private Project myProject;

    private ConfigurationListPopupStep(@NotNull final Project project, @NotNull final String title) {
      super(title, createSettingsList(project));
      myProject = project;
    }

    private static RunnerAndConfigurationSettingsImpl[] createSettingsList(@NotNull final Project project) {
      final RunManagerEx manager = RunManagerEx.getInstanceEx(project);

      final List<RunnerAndConfigurationSettingsImpl> result = new ArrayList<RunnerAndConfigurationSettingsImpl>();
      final ConfigurationType[] factories = manager.getConfigurationFactories();
      for (final ConfigurationType factory : factories) {
        final RunnerAndConfigurationSettingsImpl[] configurations = manager.getConfigurationSettings(factory);
        result.addAll(Arrays.asList(configurations));
      }

      return result.toArray(new RunnerAndConfigurationSettingsImpl[result.size()]);
    }

    @Override
    public ListSeparator getSeparatorAbove(RunnerAndConfigurationSettingsImpl value) {
      final List<RunnerAndConfigurationSettingsImpl> configurations = getValues();
      final int index = configurations.indexOf(value);
      if (index > 0 && index <= configurations.size() - 1) {
        final RunnerAndConfigurationSettingsImpl aboveConfiguration = index == 0 ? null : configurations.get(index - 1);
        final ConfigurationType currentType = value.getType();
        final ConfigurationType aboveType = aboveConfiguration == null ? null : aboveConfiguration.getType();
        if (aboveType != currentType && currentType != null) {
          return new ListSeparator(); // new ListSeparator(currentType.getDisplayName());
        }
      }

      return null;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public int getDefaultOptionIndex() {
      return getValues().indexOf(RunManager.getInstance(myProject).getSelectedConfiguration());
    }

    @Override
    public PopupStep onChosen(final RunnerAndConfigurationSettingsImpl settings, boolean finalChoice) {
      final Executor executor = ExecuteRunConfigurationAction.getActionInstance().getExecutor();
      if (finalChoice && ExecutionUtil.getRunner(executor.getId(), settings) != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            RunManagerEx.getInstanceEx(myProject).setSelectedConfiguration(settings);
            ExecutionUtil.executeConfiguration(myProject, settings, executor,
                                               DataManager.getInstance().getDataContext());
          }
        });

        return FINAL_CHOICE;
      }
      else {
        return new ConfigurationActionsStep(myProject, settings);
      }
    }

    @Override
    public boolean hasSubstep(RunnerAndConfigurationSettingsImpl selectedValue) {
      return true;
    }

    @NotNull
    @Override
    public String getTextFor(RunnerAndConfigurationSettingsImpl value) {
      return value.getConfiguration().getName();
    }

    @Override
    public Icon getIconFor(RunnerAndConfigurationSettingsImpl value) {
      return ExecutionUtil.getConfigurationIcon(myProject, value);
    }
  }

  private static final class ConfigurationActionsStep extends BaseListPopupStep<ActionWrapper> {
    private ConfigurationActionsStep(@NotNull final Project project,
                                     @NotNull final RunnerAndConfigurationSettingsImpl settings) {
      super("Actions", buildActions(project, settings));
    }

    private static ActionWrapper[] buildActions(@NotNull final Project project,
                                                @NotNull final RunnerAndConfigurationSettingsImpl settings) {
      final List<ActionWrapper> result = new ArrayList<ActionWrapper>();
      final Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
      for (final Executor executor : executors) {
        final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), settings.getConfiguration());
        if (runner != null) {
          result.add(new ActionWrapper(executor.getActionName(), executor.getIcon()) {
            @Override
            public void perform() {
              RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);

              ExecuteRunConfigurationAction.getActionInstance().setExecutor(executor, project);
              ExecutionUtil.executeConfiguration(project, settings, executor, DataManager.getInstance().getDataContext());
            }
          });
        }
      }

      result.add(new ActionWrapper("Edit...", EDIT_ICON) {
        @Override
        public void perform() {
          RunDialog.editConfiguration(project, settings, "Edit configuration settings");
        }
      });

      if (RunManager.getInstance(project).isTemporary(settings.getConfiguration())) {
        result.add(new ActionWrapper("Save temp configuration", SAVE_ICON) {
          @Override
          public void perform() {
            RunManager.getInstance(project).makeStable(settings.getConfiguration());
          }
        });
      }

      return result.toArray(new ActionWrapper[result.size()]);
    }

    @Override
    public PopupStep onChosen(final ActionWrapper selectedValue, boolean finalChoice) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          selectedValue.perform();
        }
      });

      return FINAL_CHOICE;
    }

    @Override
    public Icon getIconFor(ActionWrapper aValue) {
      return aValue.getIcon();
    }

    @NotNull
    @Override
    public String getTextFor(ActionWrapper value) {
      return value.getName();
    }
  }

  private abstract static class ActionWrapper {
    private String myName;
    private Icon myIcon;

    private ActionWrapper(String name, Icon icon) {
      myName = name;
      myIcon = icon;
    }

    public abstract void perform();

    public String getName() {
      return myName;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }
}

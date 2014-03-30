/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.Semaphore;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Vassiliy Kudryashov
 */
public class RunConfigurationBeforeRunProvider
extends BeforeRunTaskProvider<RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask> {

  public static final Key<RunConfigurableBeforeRunTask> ID = Key.create("RunConfigurationTask");

  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunConfigurationBeforeRunProvider");

  private final Project myProject;

  public RunConfigurationBeforeRunProvider(Project project) {
    myProject = project;
  }

  @Override
  public Key<RunConfigurableBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Actions.Execute;
  }

  @Override
  public Icon getTaskIcon(RunConfigurableBeforeRunTask task) {
    if (task.getSettings() == null)
      return null;
    return ProgramRunnerUtil.getConfigurationIcon(task.getSettings(), false);
  }

  @Override
  public String getName() {
    return ExecutionBundle.message("before.launch.run.another.configuration");
  }

  @Override
  public String getDescription(RunConfigurableBeforeRunTask task) {
    if (task.getSettings() == null) {
      return ExecutionBundle.message("before.launch.run.another.configuration");
    }
    else {
      return ExecutionBundle.message("before.launch.run.certain.configuration", task.getSettings().getName());
    }
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  @Nullable
  public RunConfigurableBeforeRunTask createTask(RunConfiguration runConfiguration) {
    if (runConfiguration.getProject().isInitialized()) {
      Collection<RunnerAndConfigurationSettings> configurations =
        RunManagerImpl.getInstanceImpl(runConfiguration.getProject()).getSortedConfigurations();
      if (configurations.isEmpty()
          || (configurations.size() == 1 && configurations.iterator().next().getConfiguration() == runConfiguration)) {
        return null;
      }
    }
    return new RunConfigurableBeforeRunTask();
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, RunConfigurableBeforeRunTask task) {
    SelectionDialog dialog =
      new SelectionDialog(task.getSettings(), getAvailableConfigurations(runConfiguration));
    dialog.show();
    RunnerAndConfigurationSettings settings = dialog.getSelectedSettings();
    if (settings != null) {
      task.setSettings(settings);
      return true;
    }
    else {
      return false;
    }
  }

  @NotNull
  private List<RunnerAndConfigurationSettings> getAvailableConfigurations(RunConfiguration runConfiguration) {
    Project project = runConfiguration.getProject();
    if (project == null || !project.isInitialized())
      return Collections.emptyList();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);

    final ArrayList<RunnerAndConfigurationSettings> configurations
      = new ArrayList<RunnerAndConfigurationSettings>(runManager.getSortedConfigurations());
    String executorId = DefaultRunExecutor.getRunExecutorInstance().getId();
    for (Iterator<RunnerAndConfigurationSettings> iterator = configurations.iterator(); iterator.hasNext();) {
      RunnerAndConfigurationSettings settings = iterator.next();
      final ProgramRunner runner = ProgramRunnerUtil.getRunner(executorId, settings);
      if (runner == null || settings.getConfiguration() == runConfiguration)
        iterator.remove();
    }
    return configurations;
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration,
                                RunConfigurableBeforeRunTask task) {
    RunnerAndConfigurationSettings settings = task.getSettings();
    if (settings == null) {
      return false;
    }
    String executorId = DefaultRunExecutor.getRunExecutorInstance().getId();
    final ProgramRunner runner = ProgramRunnerUtil.getRunner(executorId, settings);
    if (runner == null)
      return false;
    return runner.canRun(executorId, settings.getConfiguration());
  }

  @Override
  public boolean executeTask(final DataContext dataContext,
                             RunConfiguration configuration,
                             final ExecutionEnvironment env,
                             RunConfigurableBeforeRunTask task) {
    RunnerAndConfigurationSettings settings = task.getSettings();
    if (settings == null) {
      return false;
    }
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    String executorId = executor.getId();
    final ProgramRunner runner = ProgramRunnerUtil.getRunner(executorId, settings);
    if (runner == null)
      return false;
    final ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, settings, myProject);
    environment.setExecutionId(env.getExecutionId());
    if (!ExecutionTargetManager.canRun(settings, env.getExecutionTarget())) {
      return false;
    }

    if (!runner.canRun(executorId, environment.getRunProfile())) {
      return false;
    }

    else {
      final Semaphore targetDone = new Semaphore();
      final boolean[] result = new boolean[1];
      try {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {

          @Override
          public void run() {
            targetDone.down();
            try {
              runner.execute(environment, new ProgramRunner.Callback() {
                @Override
                public void processStarted(RunContentDescriptor descriptor) {
                  ProcessHandler processHandler = descriptor != null ? descriptor.getProcessHandler() : null;
                  if (processHandler != null) {
                    processHandler.addProcessListener(new ProcessAdapter() {
                      @Override
                      public void processTerminated(ProcessEvent event) {
                        result[0] = event.getExitCode() == 0;
                        targetDone.up();
                      }
                    });
                  }
                }
              });
            }
            catch (ExecutionException e) {
              LOG.error(e);
            }
          }
        }, ModalityState.NON_MODAL);
      }
      catch (Exception e) {
        LOG.error(e);
        return false;
      }
      targetDone.waitFor();
      return result[0];
    }
  }

  class RunConfigurableBeforeRunTask extends BeforeRunTask<RunConfigurableBeforeRunTask> {
    private String myConfigurationName;
    private String myConfigurationType;
    private boolean myInitialized = false;

    private RunnerAndConfigurationSettings mySettings;

    RunConfigurableBeforeRunTask() {
      super(ID);
    }

    @Override
    public void writeExternal(Element element) {
      super.writeExternal(element);
      if (myConfigurationName != null && myConfigurationType != null) {
        element.setAttribute("run_configuration_name", myConfigurationName);
        element.setAttribute("run_configuration_type", myConfigurationType);
      }
      else if (mySettings != null) {
        element.setAttribute("run_configuration_name", mySettings.getName());
        element.setAttribute("run_configuration_type", mySettings.getType().getId());
      }
    }

    @Override
    public void readExternal(Element element) {
      super.readExternal(element);
      Attribute configurationNameAttr = element.getAttribute("run_configuration_name");
      Attribute configurationTypeAttr = element.getAttribute("run_configuration_type");
      myConfigurationName = configurationNameAttr != null ? configurationNameAttr.getValue() : null;
      myConfigurationType = configurationTypeAttr != null ? configurationTypeAttr.getValue() : null;
    }

    void init() {
      if (myInitialized) {
        return;
      }
      if (myConfigurationName != null && myConfigurationType != null) {
        Collection<RunnerAndConfigurationSettings> configurations = RunManagerImpl.getInstanceImpl(myProject).getSortedConfigurations();
        for (RunnerAndConfigurationSettings runConfiguration : configurations) {
          ConfigurationType type = runConfiguration.getType();
          if (myConfigurationName.equals(runConfiguration.getName())
              && type != null
              && myConfigurationType.equals(type.getId())) {
            setSettings(runConfiguration);
            return;
          }
        }
      }
    }

    void setSettings(RunnerAndConfigurationSettings settings) {
      mySettings = settings;
      myInitialized = true;
    }

    RunnerAndConfigurationSettings getSettings() {
      init();
      return mySettings;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      RunConfigurableBeforeRunTask that = (RunConfigurableBeforeRunTask)o;

      if (myConfigurationName != null ? !myConfigurationName.equals(that.myConfigurationName) : that.myConfigurationName != null) return false;
      if (myConfigurationType != null ? !myConfigurationType.equals(that.myConfigurationType) : that.myConfigurationType != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myConfigurationName != null ? myConfigurationName.hashCode() : 0);
      result = 31 * result + (myConfigurationType != null ? myConfigurationType.hashCode() : 0);
      return result;
    }
  }

  private class SelectionDialog extends DialogWrapper {
    private RunnerAndConfigurationSettings mySelectedSettings;
    @NotNull private final List<RunnerAndConfigurationSettings> mySettings;
    private JBList myJBList;

    private SelectionDialog(RunnerAndConfigurationSettings selectedSettings, @NotNull List<RunnerAndConfigurationSettings> settings) {
      super(myProject);
      setTitle(ExecutionBundle.message("before.launch.run.another.configuration.choose"));
      mySelectedSettings = selectedSettings;
      mySettings = settings;
      init();
      myJBList.setSelectedValue(mySelectedSettings, true);
      FontMetrics fontMetrics = myJBList.getFontMetrics(myJBList.getFont());
      int maxWidth = fontMetrics.stringWidth("m") * 30;
      for (RunnerAndConfigurationSettings setting : settings) {
        maxWidth = Math.max(fontMetrics.stringWidth(setting.getConfiguration().getName()), maxWidth);
      }
      maxWidth += 24;//icon and gap
      myJBList.setMinimumSize(new Dimension(maxWidth, myJBList.getPreferredSize().height));
    }

    @Nullable
    @Override
    protected String getDimensionServiceKey() {
      return "com.intellij.execution.impl.RunConfigurationBeforeRunProvider.dimensionServiceKey;";
    }

    @Override
    protected JComponent createCenterPanel() {
      myJBList = new JBList(mySettings);
      myJBList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myJBList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          Object selectedValue = myJBList.getSelectedValue();
          if (selectedValue instanceof RunnerAndConfigurationSettings) {
            mySelectedSettings = (RunnerAndConfigurationSettings)selectedValue;
          }
          else {
            mySelectedSettings = null;
          }
          setOKActionEnabled(mySelectedSettings != null);
        }
      });
      myJBList.setCellRenderer(new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof RunnerAndConfigurationSettings) {
            RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)value;
            RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
            setIcon(runManager.getConfigurationIcon(settings));
            RunConfiguration configuration = settings.getConfiguration();
            append(configuration.getName(), settings.isTemporary()
                                            ? SimpleTextAttributes.GRAY_ATTRIBUTES
                                            : SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      });
      return new JBScrollPane(myJBList);
    }

    @Nullable
    RunnerAndConfigurationSettings getSelectedSettings() {
      return isOK() ? mySelectedSettings : null;
    }
  }
}

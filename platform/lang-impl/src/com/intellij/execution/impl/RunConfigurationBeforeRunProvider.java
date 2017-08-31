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
package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vassiliy Kudryashov
 */
public class RunConfigurationBeforeRunProvider
extends BeforeRunTaskProvider<RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask> {
  public static final Key<RunConfigurableBeforeRunTask> ID = Key.create("RunConfigurationTask");

  private static final Logger LOG = Logger.getInstance(RunConfigurationBeforeRunProvider.class);

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
  public RunConfigurableBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return createTask(runConfiguration, runConfiguration.getProject().isInitialized() ? RunManagerImpl.getInstanceImpl(runConfiguration.getProject()) : null);
  }

  @Nullable
  public RunConfigurableBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration, @Nullable RunManagerImpl runManager) {
    if (runManager != null) {
      List<RunnerAndConfigurationSettings> configurations = runManager.getAllSettings();
      if (configurations.isEmpty() || (configurations.size() == 1 && configurations.get(0).getConfiguration() == runConfiguration)) {
        return null;
      }
    }
    return new RunConfigurableBeforeRunTask();
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull RunConfigurableBeforeRunTask task) {
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
  private static List<RunnerAndConfigurationSettings> getAvailableConfigurations(@NotNull RunConfiguration runConfiguration) {
    Project project = runConfiguration.getProject();
    if (project == null || !project.isInitialized()) {
      return Collections.emptyList();
    }

    List<RunnerAndConfigurationSettings> configurations = new ArrayList<>(RunManagerImpl.getInstanceImpl(project).getAllSettings());
    String executorId = DefaultRunExecutor.getRunExecutorInstance().getId();
    for (Iterator<RunnerAndConfigurationSettings> iterator = configurations.iterator(); iterator.hasNext();) {
      RunnerAndConfigurationSettings settings = iterator.next();
      ProgramRunner runner = ProgramRunnerUtil.getRunner(executorId, settings);
      if (runner == null || settings.getConfiguration() == runConfiguration) {
        iterator.remove();
      }
    }
    return configurations;
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration,
                                @NotNull RunConfigurableBeforeRunTask task) {
    RunnerAndConfigurationSettings settings = task.getSettings();
    if (settings == null) {
      return false;
    }
    String executorId = DefaultRunExecutor.getRunExecutorInstance().getId();
    final ProgramRunner runner = ProgramRunnerUtil.getRunner(executorId, settings);
    return runner != null && runner.canRun(executorId, settings.getConfiguration());
  }

  @Override
  public boolean executeTask(final DataContext dataContext,
                             @NotNull RunConfiguration configuration,
                             @NotNull final ExecutionEnvironment env,
                             @NotNull RunConfigurableBeforeRunTask task) {
    RunnerAndConfigurationSettings settings = task.getSettings();
    if (settings == null) {
      return true; // ignore missing configurations: IDEA-155476 Run/debug silently fails when 'Run another configuration' step is broken
    }
    return doExecuteTask(env, settings);
  }

  public static boolean doExecuteTask(@NotNull final ExecutionEnvironment env, @NotNull final RunnerAndConfigurationSettings settings) {
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final String executorId = executor.getId();
    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      return false;
    }

    ExecutionTarget compatibleTarget = getCompatibleTarget(env, settings);
    if (compatibleTarget == null) {
      return false;
    }

    final ExecutionEnvironment environment = builder.target(compatibleTarget).build();
    environment.setExecutionId(env.getExecutionId());

    if (!environment.getRunner().canRun(executorId, environment.getRunProfile())) {
      return false;
    }
    else {
      beforeRun(environment);
      return doRunTask(executorId, environment, environment.getRunner());
    }
  }

  @Nullable
  private static ExecutionTarget getCompatibleTarget(@NotNull ExecutionEnvironment env, @NotNull RunnerAndConfigurationSettings settings) {
    if (ExecutionTargetManager.canRun(settings, env.getExecutionTarget())) {
      return env.getExecutionTarget();
    }
    return ContainerUtil.getFirstItem(ExecutionTargetManager.getInstance(env.getProject()).getTargetsFor(settings));
  }

  public static boolean doRunTask(final String executorId, final ExecutionEnvironment environment, ProgramRunner<?> runner) {
    final Semaphore targetDone = new Semaphore();
    final Ref<Boolean> result = new Ref<>(false);
    final Disposable disposable = Disposer.newDisposable();

    environment.getProject().getMessageBus().connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStartScheduled(@NotNull final String executorIdLocal, @NotNull final ExecutionEnvironment environmentLocal) {
        if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
          targetDone.down();
        }
      }

      @Override
      public void processNotStarted(@NotNull final String executorIdLocal, @NotNull final ExecutionEnvironment environmentLocal) {
        if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
          Boolean skipRun = environment.getUserData(ExecutionManagerImpl.EXECUTION_SKIP_RUN);
          if (skipRun != null && skipRun) {
            result.set(true);
          }
          targetDone.up();
        }
      }

      @Override
      public void processTerminated(@NotNull String executorIdLocal,
                                    @NotNull ExecutionEnvironment environmentLocal,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
          result.set(exitCode == 0);
          targetDone.up();
        }
      }
    });

    try {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          runner.execute(environment);
        }
        catch (ExecutionException e) {
          targetDone.up();
          LOG.error(e);
        }
      }, ModalityState.NON_MODAL);
    }
    catch (Exception e) {
      LOG.error(e);
      Disposer.dispose(disposable);
      return false;
    }

    targetDone.waitFor();
    Disposer.dispose(disposable);

    return result.get();
  }

  private static void beforeRun(@NotNull ExecutionEnvironment environment) {
    for (RunConfigurationBeforeRunProviderDelegate delegate : Extensions.getExtensions(RunConfigurationBeforeRunProviderDelegate.EP_NAME)) {
      delegate.beforeRun(environment);
    }
  }

  public class RunConfigurableBeforeRunTask extends BeforeRunTask<RunConfigurableBeforeRunTask> {
    private String myConfigurationName;
    private String myConfigurationType;

    private RunnerAndConfigurationSettings mySettings;

    RunConfigurableBeforeRunTask() {
      super(ID);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
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
    public void readExternal(@NotNull Element element) {
      super.readExternal(element);

      myConfigurationName = element.getAttributeValue("run_configuration_name");
      myConfigurationType = element.getAttributeValue("run_configuration_type");
    }

    // avoid RunManagerImpl.getInstanceImpl and findConfigurationByTypeAndName calls (can be called during RunManagerImpl initialization)
    boolean isMySettings(@NotNull RunnerAndConfigurationSettings settings) {
      if (mySettings != null) {
        // instance equality
        return mySettings == settings;
      }
      return settings.getType().getId().equals(myConfigurationType) && settings.getName().equals(myConfigurationName);
    }

    void init() {
      if (mySettings != null) {
        return;
      }
      if (myConfigurationType != null && myConfigurationName != null) {
        setSettings(RunManagerImpl.getInstanceImpl(myProject).findConfigurationByTypeAndName(myConfigurationType, myConfigurationName));
      }
    }

    public void setSettings(RunnerAndConfigurationSettings settings) {
      mySettings = settings;
    }

    public RunnerAndConfigurationSettings getSettings() {
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
      myJBList.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() ==2) {
            doOKAction();
          }
        }
      });
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
        protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof RunnerAndConfigurationSettings) {
            RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)value;
            setIcon(RunManagerEx.getInstanceEx(myProject).getConfigurationIcon(settings));
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

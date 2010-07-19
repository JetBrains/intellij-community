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

/*
 * User: anna
 * Date: 24-Dec-2008
 */
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AbstractRerunFailedTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction");
  private TestFrameworkRunningModel myModel;
  private Getter<TestFrameworkRunningModel> myModelProvider;
  protected TestConsoleProperties myConsoleProperties;
  protected RunnerSettings myRunnerSettings;
  protected ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;

  public void init(final TestConsoleProperties consoleProperties,
                   final RunnerSettings runnerSettings,
                   final ConfigurationPerRunnerSettings configurationSettings) {
    myConfigurationPerRunnerSettings = configurationSettings;
    myRunnerSettings = runnerSettings;
    myConsoleProperties = consoleProperties;
  }

  public void setModel(TestFrameworkRunningModel model) {
    myModel = model;
  }

  public void setModelProvider(Getter<TestFrameworkRunningModel> modelProvider) {
    myModelProvider = modelProvider;
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isActive(e));
  }

  private boolean isActive(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;
    TestFrameworkRunningModel model = getModel();
    if (model == null || model.getRoot() == null) return false;
    List<AbstractTestProxy> failed = getFailedTests(project);
    return !failed.isEmpty();
  }

  @NotNull
  protected List<AbstractTestProxy> getFailedTests(Project project) {
    final List<? extends AbstractTestProxy> myAllTests = getModel().getRoot().getAllTests();
    return Filter.FAILED_OR_INTERRUPTED.and(JavaAwareFilter.METHOD(project)).select(myAllTests);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    boolean isDebug = myConsoleProperties.isDebug();
    final MyRunProfile profile = getRunProfile();
    try {
      final Executor executor = isDebug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance();
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), profile);
      assert runner != null;
      runner.execute(executor, new ExecutionEnvironment(profile, profile.getProject(), myRunnerSettings, myConfigurationPerRunnerSettings,
                                                        null));
    }
    catch (ExecutionException e1) {
      LOG.error(e1);
    }
    finally {
      profile.clear();
    }
  }

  public MyRunProfile getRunProfile() {
    return null;
  }

  public TestFrameworkRunningModel getModel() {
    if (myModel != null) {
      return myModel;
    }
    if (myModelProvider != null) {
      return myModelProvider.get();
    }
    return null;
  }

  protected static abstract class MyRunProfile extends RunConfigurationBase implements ModuleRunProfile{
    private final RunConfigurationBase myConfiguration;

    public MyRunProfile(RunConfigurationBase configuration) {
      super(configuration.getProject(), configuration.getFactory(), ActionsBundle.message("action.RerunFailedTests.text"));
      myConfiguration = configuration;
    }

    public void clear() {    }


    public void checkConfiguration() throws RuntimeConfigurationException {}

    ///////////////////////////////////Delegates
    public void readExternal(final Element element) throws InvalidDataException {
      myConfiguration.readExternal(element);
    }

    public void writeExternal(final Element element) throws WriteExternalException {
      myConfiguration.writeExternal(element);
    }

    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfiguration.getConfigurationEditor();
    }

    @NotNull
    public ConfigurationType getType() {
      return myConfiguration.getType();
    }

    public JDOMExternalizable createRunnerSettings(final ConfigurationInfoProvider provider) {
      return myConfiguration.createRunnerSettings(provider);
    }

    public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(final ProgramRunner runner) {
      return myConfiguration.getRunnerSettingsEditor(runner);
    }

    public RunConfiguration clone() {
      return myConfiguration.clone();
    }

    public int getUniqueID() {
      return myConfiguration.getUniqueID();
    }

    public LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
      return myConfiguration.getOptionsForPredefinedLogFile(predefinedLogFile);
    }

    public ArrayList<PredefinedLogFile> getPredefinedLogFiles() {
      return myConfiguration.getPredefinedLogFiles();
    }

    public ArrayList<LogFileOptions> getAllLogFiles() {
      return myConfiguration.getAllLogFiles();
    }

    public ArrayList<LogFileOptions> getLogFiles() {
      return myConfiguration.getLogFiles();
    }
  }
}
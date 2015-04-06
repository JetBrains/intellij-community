/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.ExtensionPoints;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.PathUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

public abstract class JavaTestFrameworkRunnableState<T extends ModuleBasedConfiguration<JavaRunConfigurationModule> & CommonJavaRunConfigurationParameters> extends JavaCommandLineState {
  private static final Logger LOG = Logger.getInstance("#" + JavaTestFrameworkRunnableState.class.getName());
  protected ServerSocket myServerSocket;
  protected File myTempFile;

  public JavaTestFrameworkRunnableState(ExecutionEnvironment environment) {
    super(environment);
  }

  @NotNull protected abstract String getFrameworkName();

  @NotNull protected abstract String getFrameworkId();

  protected abstract void passTempFile(ParametersList parametersList, String tempFilePath);

  @NotNull protected abstract AbstractRerunFailedTestsAction createRerunFailedTestsAction(TestConsoleProperties testConsoleProperties, ConsoleView consoleView);

  @NotNull protected abstract T getConfiguration();

  @NotNull protected abstract OSProcessHandler createHandler(Executor executor) throws ExecutionException;

  public SearchForTestsTask createSearchingForTestsTask() {
    return null;
  }

  protected boolean configureByModule(Module module) {
    return module != null;
  }

  protected ExecutionResult startSMRunner(Executor executor) throws ExecutionException {
    if (!Registry.is(getFrameworkId() + "_sm_runner")) {
      return null;
    }
    getJavaParameters().getVMParametersList().addProperty("idea." + getFrameworkId() + ".sm_runner");
    getJavaParameters().getClassPath().add(PathUtil.getJarPathForClass(ServiceMessageTypes.class));

    final RunnerSettings runnerSettings = getRunnerSettings();

    final TestConsoleProperties testConsoleProperties = new SMTRunnerConsoleProperties(getConfiguration(), getFrameworkName(), executor);
    testConsoleProperties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);

    final BaseTestsOutputConsoleView consoleView =
      SMTestRunnerConnectionUtil.createConsoleWithCustomLocator(getFrameworkName(), testConsoleProperties, getEnvironment(), null);
    Disposer.register(getConfiguration().getProject(), consoleView);

    final OSProcessHandler handler = createHandler(executor);
    consoleView.attachToProcess(handler);
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        Runnable runnable = new Runnable() {
          public void run() {
            final SMTestRunnerResultsForm viewer = ((SMTRunnerConsoleView)consoleView).getResultsViewer();
            if (viewer.hasTestSuites() ||
                !ResetConfigurationModuleAdapter.tryWithAnotherModule(getConfiguration(), testConsoleProperties.isDebug())) {
              TestsUIUtil.notifyByBalloon(testConsoleProperties.getProject(), viewer.hasTestSuites(), viewer.getRoot(), testConsoleProperties, null);
            }
          }
        };
        SwingUtilities.invokeLater(runnable);
        handler.removeProcessListener(this);
      }

    });

    AbstractRerunFailedTestsAction rerunFailedTestsAction = createRerunFailedTestsAction(testConsoleProperties, consoleView);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      @Override
      public TestFrameworkRunningModel get() {
        return ((SMTRunnerConsoleView)consoleView).getResultsViewer();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);

    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, runnerSettings);
    return result;
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = new JavaParameters();
    final Module module = getConfiguration().getConfigurationModule().getModule();

    Project project = getConfiguration().getProject();
    Sdk jdk = module == null ? ProjectRootManager.getInstance(project).getProjectSdk() : ModuleRootManager.getInstance(module).getSdk();
    javaParameters.setJdk(jdk);


    final Object[] patchers = Extensions.getExtensions(ExtensionPoints.JUNIT_PATCHER);
    for (Object patcher : patchers) {
      ((JUnitPatcher)patcher).patchJavaParameters(module, javaParameters);
    }

    // Append coverage parameters if appropriate
    for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
      ext.updateJavaParameters(getConfiguration(), javaParameters, getRunnerSettings());
    }

    JavaParametersUtil.configureConfiguration(javaParameters, getConfiguration());
    JavaSdkUtil.addRtJar(javaParameters.getClassPath());

    configureClasspath(javaParameters);
    return javaParameters;
  }

  protected void collectListeners(JavaParameters javaParameters, StringBuilder buf, String epName, String delimiter) {
    final T configuration = getConfiguration();
    final Object[] listeners = Extensions.getExtensions(epName);
    for (final Object listener : listeners) {
      boolean enabled = true;
      for (RunConfigurationExtension ext : Extensions.getExtensions(RunConfigurationExtension.EP_NAME)) {
        if (ext.isListenerDisabled(configuration, listener, getRunnerSettings())) {
          enabled = false;
          break;
        }
      }
      if (enabled) {
        if (buf.length() > 0) buf.append(delimiter);
        final Class classListener = listener.getClass();
        buf.append(classListener.getName());
        javaParameters.getClassPath().add(PathUtil.getJarPathForClass(classListener));
      }
    }
  }

  protected void configureClasspath(final JavaParameters javaParameters) throws CantRunException {
    RunConfigurationModule module = getConfiguration().getConfigurationModule();
    final String jreHome = getConfiguration().isAlternativeJrePathEnabled() ? getConfiguration().getAlternativeJrePath() : null;
    final int pathType = JavaParameters.JDK_AND_CLASSES_AND_TESTS;
    if (configureByModule(module.getModule())) {
      JavaParametersUtil.configureModule(module, javaParameters, pathType, jreHome);
    }
    else {
      JavaParametersUtil.configureProject(getConfiguration().getProject(), javaParameters, pathType, jreHome);
    }
  }

  protected void createServerSocket(JavaParameters javaParameters) {
    try {
      myServerSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      javaParameters.getProgramParametersList().add("-socket" + myServerSocket.getLocalPort());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected void createTempFiles(JavaParameters javaParameters) {
    try {
      myTempFile = FileUtil.createTempFile("idea_" + getFrameworkId(), ".tmp");
      myTempFile.deleteOnExit();
      passTempFile(javaParameters.getProgramParametersList(), myTempFile.getAbsolutePath());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }


}

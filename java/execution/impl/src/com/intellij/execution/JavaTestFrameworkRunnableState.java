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

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.util.PathUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import org.jetbrains.annotations.NotNull;

public abstract class JavaTestFrameworkRunnableState extends JavaCommandLineState {
  public JavaTestFrameworkRunnableState(ExecutionEnvironment environment) {
    super(environment);
  }

  @NotNull protected abstract String getFrameworkName();

  @NotNull protected abstract String getVMParameter();

  @NotNull protected abstract AbstractRerunFailedTestsAction createRerunFailedTestsAction(TestConsoleProperties testConsoleProperties, ConsoleView consoleView);

  protected ExecutionResult startSMRunner(Executor executor,
                                          OSProcessHandler handler,
                                          RunConfigurationBase configuration,
                                          ExecutionEnvironment environment) throws ExecutionException {
    getJavaParameters().getVMParametersList().add(getVMParameter());
    getJavaParameters().getClassPath().add(PathUtil.getJarPathForClass(ServiceMessageTypes.class));

    final RunnerSettings runnerSettings = getRunnerSettings();

    TestConsoleProperties testConsoleProperties = new SMTRunnerConsoleProperties(configuration, getFrameworkName(), executor);
    testConsoleProperties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);

    final ConsoleView consoleView = SMTestRunnerConnectionUtil.createConsoleWithCustomLocator(getFrameworkName(), testConsoleProperties, environment, null);
    Disposer.register(configuration.getProject(), consoleView);
    consoleView.attachToProcess(handler);

    AbstractRerunFailedTestsAction rerunFailedTestsAction = createRerunFailedTestsAction(testConsoleProperties, consoleView);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      @Override
      public TestFrameworkRunningModel get() {
        return ((SMTRunnerConsoleView)consoleView).getResultsViewer();
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);

    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(configuration, handler, runnerSettings);
    return result;
  }
}

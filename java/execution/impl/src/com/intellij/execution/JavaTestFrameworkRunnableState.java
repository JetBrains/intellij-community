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
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.rt.execution.CommandLineWrapper;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Locale;

public abstract class JavaTestFrameworkRunnableState<T extends ModuleBasedConfiguration<JavaRunConfigurationModule> & CommonJavaRunConfigurationParameters & SMRunnerConsolePropertiesProvider> extends JavaCommandLineState {
  private static final Logger LOG = Logger.getInstance("#" + JavaTestFrameworkRunnableState.class.getName());
  protected ServerSocket myServerSocket;
  protected File myTempFile;

  public JavaTestFrameworkRunnableState(ExecutionEnvironment environment) {
    super(environment);
  }

  @NotNull protected abstract String getFrameworkName();

  @NotNull protected abstract String getFrameworkId();

  protected abstract void passTempFile(ParametersList parametersList, String tempFilePath);

  @NotNull protected abstract T getConfiguration();

  @Nullable protected abstract TestSearchScope getScope();

  @NotNull protected abstract String getForkMode();

  @NotNull protected abstract OSProcessHandler createHandler(Executor executor) throws ExecutionException;

  public SearchForTestsTask createSearchingForTestsTask() {
    return null;
  }

  protected boolean configureByModule(Module module) {
    return module != null;
  }

  protected ExecutionResult startSMRunner(Executor executor) throws ExecutionException {
    if (!isSmRunnerUsed()) {
      return null;
    }
    getJavaParameters().getVMParametersList().addProperty("idea." + getFrameworkId() + ".sm_runner");

    final RunnerSettings runnerSettings = getRunnerSettings();

    final SMTRunnerConsoleProperties testConsoleProperties = getConfiguration().createTestConsoleProperties(executor);
    testConsoleProperties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);

    final BaseTestsOutputConsoleView consoleView = SMTestRunnerConnectionUtil.createConsole(getFrameworkName(), testConsoleProperties);
    final SMTestRunnerResultsForm viewer = ((SMTRunnerConsoleView)consoleView).getResultsViewer();
    Disposer.register(getConfiguration().getProject(), consoleView);

    final OSProcessHandler handler = createHandler(executor);
    consoleView.attachToProcess(handler);
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(ProcessEvent event) {
        if (getConfiguration().isSaveOutputToFile()) {
          viewer.getRoot().setOutputFilePath(getConfiguration().getOutputFilePath());
        }
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        Runnable runnable = new Runnable() {
          public void run() {
            viewer.getRoot().flush();
            deleteTempFiles();
            clear();
          }
        };
        UIUtil.invokeLaterIfNeeded(runnable);
        handler.removeProcessListener(this);
      }
    });

    AbstractRerunFailedTestsAction rerunFailedTestsAction = testConsoleProperties.createRerunFailedTestsAction(consoleView);
    LOG.assertTrue(rerunFailedTestsAction != null);
    rerunFailedTestsAction.setModelProvider(new Getter<TestFrameworkRunningModel>() {
      @Override
      public TestFrameworkRunningModel get() {
        return viewer;
      }
    });

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction);

    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, runnerSettings);
    return result;
  }

  protected boolean isSmRunnerUsed() {
    return Registry.is(getFrameworkId() + "_sm");
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

    final String parameters = getConfiguration().getProgramParameters();
    getConfiguration().setProgramParameters(null);
    try {
      JavaParametersUtil.configureConfiguration(javaParameters, getConfiguration());
    }
    finally {
      getConfiguration().setProgramParameters(parameters);
    }
    javaParameters.getClassPath().addFirst(JavaSdkUtil.getIdeaRtJarPath());
    configureClasspath(javaParameters);

    if (!StringUtil.isEmptyOrSpaces(parameters)) {
      javaParameters.getProgramParametersList().add("@name" + parameters);
    }

    return javaParameters;
  }

  private ServerSocket myForkSocket = null;

  @Nullable
  public ServerSocket getForkSocket() {
    if (myForkSocket == null && (!Comparing.strEqual(getForkMode(), "none") || forkPerModule()) && getRunnerSettings() != null) {
      try {
        myForkSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return myForkSocket;
  }

  private boolean isExecutorDisabledInForkedMode() {
    final RunnerSettings settings = getRunnerSettings();
    return settings != null && !(settings instanceof GenericDebuggerRunnerSettings);
  }

  protected void appendForkInfo(Executor executor) throws ExecutionException {
    final String forkMode = getForkMode();
    if (Comparing.strEqual(forkMode, "none")) {
      if (forkPerModule()) {
        if (isExecutorDisabledInForkedMode()) {
          final String actionName = UIUtil.removeMnemonic(executor.getStartActionText());
          throw new CantRunException("'" + actionName + "' is disabled when per-module working directory is configured.<br/>" +
                                     "Please specify single working directory, or change test scope to single module.");
        }
      } else {
        return;
      }
    } else if (isExecutorDisabledInForkedMode()) {
      final String actionName = executor.getActionName();
      throw new CantRunException(actionName + " is disabled in fork mode.<br/>Please change fork mode to &lt;none&gt; to " + actionName.toLowerCase(
        Locale.ENGLISH) + ".");
    }

    final JavaParameters javaParameters = getJavaParameters();
    final Sdk jdk = javaParameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
    }

    try {
      final File tempFile = FileUtil.createTempFile("command.line", "", true);
      final PrintWriter writer = new PrintWriter(tempFile, CharsetToolkit.UTF8);
      try {
        if (JdkUtil.useDynamicClasspath(getConfiguration().getProject())) {
          String classpath = PathUtil.getJarPathForClass(CommandLineWrapper.class);
          final String utilRtPath = PathUtil.getJarPathForClass(StringUtilRt.class);
          if (!classpath.equals(utilRtPath)) {
            classpath += File.pathSeparator + utilRtPath;
          }
          writer.println(classpath);
        }
        else {
          writer.println("");
        }

        writer.println(((JavaSdkType)jdk.getSdkType()).getVMExecutablePath(jdk));
        for (String vmParameter : javaParameters.getVMParametersList().getList()) {
          writer.println(vmParameter);
        }
      }
      finally {
        writer.close();
      }

      passForkMode(forkMode, tempFile);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  protected abstract void passForkMode(String forkMode, File tempFile) throws ExecutionException;

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

  private boolean spansMultipleModules() {
    final String qualifiedName = getConfiguration().getPackage();
    if (qualifiedName != null) {
      final Project project = getConfiguration().getProject();
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qualifiedName);
      if (aPackage != null) {
        final TestSearchScope scope = getScope();
        if (scope != null) {
          final SourceScope sourceScope = scope.getSourceScope(getConfiguration());
          if (sourceScope != null) {
            final GlobalSearchScope configurationSearchScope = GlobalSearchScopesCore.projectTestScope(project).intersectWith(
              sourceScope.getGlobalSearchScope());
            final PsiDirectory[] directories = aPackage.getDirectories(configurationSearchScope);
            return directories.length > 1;
          }
        }
      }
    }
    return false;
  }

  /**
   * Configuration based on package which spans multiple modules
   */
  protected boolean forkPerModule() {
    final String workingDirectory = getConfiguration().getWorkingDirectory();
    return getScope() != TestSearchScope.SINGLE_MODULE &&
           ("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$").equals(workingDirectory) &&
           spansMultipleModules();
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

  protected void deleteTempFiles() {
    if (myTempFile != null) {
      FileUtil.delete(myTempFile);
    }
  }

}

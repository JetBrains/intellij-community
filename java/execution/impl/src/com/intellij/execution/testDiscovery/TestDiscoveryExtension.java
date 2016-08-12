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
package com.intellij.execution.testDiscovery;

import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.TestDiscoveryListener;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestDiscoveryExtension extends RunConfigurationExtension {
  private static final Logger LOG = Logger.getInstance("#" + TestDiscoveryExtension.class.getName());

  @Nullable
  public SettingsEditor createEditor(@NotNull RunConfigurationBase configuration) {
    return null;
  }

  @Nullable
  public String getEditorTitle() {
    return null;
  }

  @NotNull
  @Override
  public String getSerializationId() {
    return "testDiscovery";
  }

  @Override
  protected void attachToProcess(@NotNull final RunConfigurationBase configuration,
                                 @NotNull final ProcessHandler handler,
                                 @Nullable RunnerSettings runnerSettings) {
    if (runnerSettings == null && isApplicableFor(configuration)) {
      final String frameworkPrefix = ((JavaTestConfigurationBase)configuration).getFrameworkPrefix();
      final String moduleName = ((JavaTestConfigurationBase)configuration).getConfigurationModule().getModuleName();

      final Alarm processTracesAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, null);
      final MessageBusConnection connection = configuration.getProject().getMessageBus().connect();
      connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, new SMTRunnerEventsAdapter() {
        private List<String> myCompletedMethodNames = new ArrayList<>();
        @Override
        public void onTestFinished(@NotNull SMTestProxy test) {
          final SMTestProxy.SMRootTestProxy root = test.getRoot();
          if ((root == null || root.getHandler() == handler)) {
            final String fullTestName = test.getLocationUrl();
            if (fullTestName != null && fullTestName.startsWith(JavaTestLocator.TEST_PROTOCOL)) {
              myCompletedMethodNames.add(frameworkPrefix + fullTestName.substring(JavaTestLocator.TEST_PROTOCOL.length() + 3));
              if (myCompletedMethodNames.size() > 50) {
                final String[] fullTestNames = ArrayUtil.toStringArray(myCompletedMethodNames);
                myCompletedMethodNames.clear();
                processTracesAlarm.addRequest(() -> processAvailableTraces(fullTestNames,
                                                                           getTracesDirectory(configuration), moduleName, frameworkPrefix,
                                                                           TestDiscoveryIndex.getInstance(configuration.getProject())
                ), 100);
              }
            }
          }
        }

        @Override
        public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
          if (testsRoot.getHandler() == handler) {
            processTracesAlarm.cancelAllRequests();
            processTracesAlarm.addRequest(() -> {
              processAvailableTraces(configuration);
              Disposer.dispose(processTracesAlarm);
            }, 0);
            connection.disconnect();
          }
        }
      });
    }
  }

  public void updateJavaParameters(RunConfigurationBase configuration, JavaParameters params, RunnerSettings runnerSettings) {
    if (runnerSettings != null || !isApplicableFor(configuration)) {
      return;
    }
    StringBuilder argument = new StringBuilder("-javaagent:");
    final String agentPath = PathUtil.getJarPathForClass(ProjectData.class);//todo spaces
    argument.append(agentPath);
    params.getVMParametersList().add(argument.toString());
    params.getClassPath().add(agentPath);
    params.getVMParametersList().addProperty(ProjectData.TRACE_DIR, getTracesDirectory(configuration));
  }

  @NotNull
  private static String getTracesDirectory(RunConfigurationBase configuration) {
    return baseTestDiscoveryPathForProject(configuration.getProject()) + File.separator + configuration.getUniqueID();
  }

  @Override
  public boolean isListenerDisabled(RunConfigurationBase configuration, Object listener, RunnerSettings runnerSettings) {
    return listener instanceof TestDiscoveryListener && (runnerSettings != null || !isApplicableFor(configuration));
  }

  @Override
  public void readExternal(@NotNull final RunConfigurationBase runConfiguration, @NotNull Element element) throws InvalidDataException {}

  @Override
  public void writeExternal(@NotNull RunConfigurationBase runConfiguration, @NotNull Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  protected boolean isApplicableFor(@NotNull final RunConfigurationBase configuration) {
    return configuration instanceof JavaTestConfigurationBase && Registry.is("testDiscovery.enabled");
  }

  @NotNull
  public static String baseTestDiscoveryPathForProject(Project project) {
    return PathManager.getSystemPath() + File.separator + "testDiscovery" + File.separator + project.getName() + "." + project.getLocationHash();
  }

  private static final Object ourTracesLock = new Object();
  
  private static void processAvailableTraces(RunConfigurationBase configuration) {
    final String tracesDirectory = getTracesDirectory(configuration);
    final TestDiscoveryIndex coverageIndex = TestDiscoveryIndex.getInstance(configuration.getProject());
    synchronized (ourTracesLock) {
      final File tracesDirectoryFile = new File(tracesDirectory);
      final File[] testMethodTraces = tracesDirectoryFile.listFiles((dir, name) -> name.endsWith(".tr"));
      if (testMethodTraces != null) {
        for (File testMethodTrace : testMethodTraces) {
          try {
            coverageIndex.updateFromTestTrace(testMethodTrace, ((JavaTestConfigurationBase)configuration).getConfigurationModule().getModuleName(),
                                              ((JavaTestConfigurationBase)configuration).getFrameworkPrefix());
            FileUtil.delete(testMethodTrace);
          }
          catch (IOException e) {
            LOG.error("Can not load " + testMethodTrace, e);
          }
        }

        final String[] filesInTracedDirectories = tracesDirectoryFile.list();
        if (filesInTracedDirectories == null || filesInTracedDirectories.length == 0) {
          FileUtil.delete(tracesDirectoryFile);
        }
      }
    }
  }

  public static void processAvailableTraces(final String[] fullTestNames,
                                            final String tracesDirectory,
                                            final String moduleName,
                                            final String frameworkPrefix,
                                            final TestDiscoveryIndex discoveryIndex) {
    synchronized (ourTracesLock) {
      for (String fullTestName : fullTestNames) {
        final String className = StringUtil.getPackageName(fullTestName);
        final String methodName = StringUtil.getShortName(fullTestName);
        if (!StringUtil.isEmptyOrSpaces(className) && !StringUtil.isEmptyOrSpaces(methodName)) {
          final File testMethodTrace = new File(tracesDirectory, className + "-" + methodName + ".tr");
          if (testMethodTrace.exists()) {
            try {
              discoveryIndex.updateFromTestTrace(testMethodTrace, moduleName, frameworkPrefix);
              FileUtil.delete(testMethodTrace);
            }
            catch (Throwable e) {
              LOG.error("Can not load " + testMethodTrace, e);
            }
          }
        }
      } 
    }
    
  }
}
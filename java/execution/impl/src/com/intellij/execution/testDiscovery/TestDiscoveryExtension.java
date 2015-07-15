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
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.testme.instrumentation.ProjectData;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

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
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(ProcessEvent event) {
          final String tracesDirectory = getTracesDirectory(configuration);
          final TestDiscoveryIndex coverageIndex = TestDiscoveryIndex.getInstance(configuration.getProject());
          final File[] testMethodTraces = new File(tracesDirectory).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
              return name.endsWith(".tr");
            }
          });
          if (testMethodTraces != null) {
            for (File testMethodTrace : testMethodTraces) {
              try {
                coverageIndex.updateFromTestTrace(testMethodTrace);
                FileUtil.delete(testMethodTrace);
              }
              catch (IOException e) {
                LOG.error("Can not load " + testMethodTrace, e);
                
              }
            }
          }
          handler.removeProcessListener(this);
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
    return !(listener instanceof TestDiscoveryListener) || runnerSettings != null;
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
}
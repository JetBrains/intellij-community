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
package com.intellij.debugger.engine;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.xdebugger.attach.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author egor
 */
public class JavaAttachDebuggerProvider implements XLocalAttachDebuggerProvider {
  private static final XAttachDebugger<LocalAttachSettings> ourAttachDebugger = new XAttachDebugger<LocalAttachSettings>() {
    @NotNull
    @Override
    public String getDebuggerDisplayName() {
      return "Java Debugger";
    }

    @Override
    public void attachDebugSession(@NotNull Project project, @NotNull LocalAttachSettings settings) throws ExecutionException {
      Pair<String, Integer> address = getAttachAddress(settings.getInfo());
      assert address != null;

      // TODO: first need to remove circular dependency with execution-impl
      //RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project)
      //  .createRunConfiguration(StringUtil.notNullize(address.first) + ":" + address.second,
      //                          RemoteConfigurationType.getInstance().getFactory());
      //
      //RemoteConfiguration configuration = (RemoteConfiguration)runSettings.getConfiguration();
      //configuration.HOST = address.first;
      //configuration.PORT = String.valueOf(address.second);
      //configuration.USE_SOCKET_TRANSPORT = true;
      //configuration.SERVER_MODE = false;

      String name = getAttachString(address);
      RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project)
        .createRunConfiguration(name, ConfigurationTypeUtil.findConfigurationType("Remote").getConfigurationFactories()[0]);

      RunConfiguration remoteConfiguration = runSettings.getConfiguration();
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, String.class, "HOST", address.first);
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, String.class, "PORT", String.valueOf(address.second));
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, boolean.class, "USE_SOCKET_TRANSPORT", true);
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, boolean.class, "SERVER_MODE", false);

      ProgramRunnerUtil.executeConfiguration(project, runSettings, DefaultDebugExecutor.getDebugExecutorInstance());
    }
  };

  private static String getAttachString(Pair<String, Integer> address) {
    return StringUtil.notNullize(address.first) + ":" + address.second;
  }

  private static final XAttachGroup<LocalAttachSettings> ourAttachGroup = new XDefaultAttachGroup<LocalAttachSettings>() {
    @Override
    public int getOrder() {
      return 1;
    }

    @NotNull
    @Override
    public String getGroupName() {
      return "Java";
    }

    @NotNull
    @Override
    public String getItemDisplayText(@NotNull Project project, @NotNull LocalAttachSettings settings, @NotNull UserDataHolder dataHolder) {
      Pair<String, Integer> address = getAttachAddress(settings.getInfo());
      assert address != null;
      return StringUtil.notNullize(ArrayUtil.getLastElement(settings.getInfo().getCommandLine().split(" "))) +
             " (" +
             getAttachString(address) +
             ')';
    }
  };

  @NotNull
  @Override
  public XAttachGroup<LocalAttachSettings> getAttachGroup() {
    return ourAttachGroup;
  }

  @NotNull
  @Override
  public List<XAttachDebugger<LocalAttachSettings>> getAvailableDebuggers(@NotNull Project project,
                                                                          @NotNull LocalAttachSettings settings,
                                                                          @NotNull UserDataHolder contextHolder) {
    Pair<String, Integer> address = getAttachAddress(settings.getInfo());
    if (address != null) {
      return Collections.singletonList(ourAttachDebugger);
    }
    return Collections.emptyList();
  }

  private static Pair<String, Integer> getAttachAddress(ProcessInfo processInfo) {
    return DebugAttachDetector.getAttachAddress(StringUtil.split(processInfo.getCommandLine(), " "));
  }
}

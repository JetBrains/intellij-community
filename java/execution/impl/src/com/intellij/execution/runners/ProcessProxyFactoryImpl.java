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
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMain;

import java.io.File;

public class ProcessProxyFactoryImpl extends ProcessProxyFactory {
  private static final boolean ourMayUseLauncher = !Boolean.getBoolean("idea.no.launcher");

  @Override
  public ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException {
    JavaParameters javaParameters = javaCmdLine.getJavaParameters();
    String mainClass = javaParameters.getMainClass();

    if (ourMayUseLauncher && mainClass != null) {
      try {
        ProcessProxyImpl proxy = new ProcessProxyImpl(StringUtil.getShortName(mainClass));

        String rtJarPath = JavaSdkUtil.getIdeaRtJarPath();
        String port = String.valueOf(proxy.getPortNumber());
        String binPath = PathManager.getBinPath();

        if (new File(rtJarPath).isFile() && JavaSdkUtil.isAtLeast(javaParameters.getJdk(), JavaSdkVersion.JDK_1_5)) {
          javaParameters.getVMParametersList().add("-javaagent:" + rtJarPath + '=' + port + ':' + binPath);
        }
        else {
          JavaSdkUtil.addRtJar(javaParameters.getClassPath());

          ParametersList vmParametersList = javaParameters.getVMParametersList();
          vmParametersList.defineProperty(AppMain.LAUNCHER_PORT_NUMBER, port);
          vmParametersList.defineProperty(AppMain.LAUNCHER_BIN_PATH, binPath);

          javaParameters.getProgramParametersList().prepend(mainClass);
          javaParameters.setMainClass(AppMain.class.getName());
        }

        return proxy;
      }
      catch (Exception e) {
        Logger.getInstance(ProcessProxy.class).warn(e);
      }
    }

    return null;
  }

  @Override
  public ProcessProxy getAttachedProxy(ProcessHandler processHandler) {
    return ProcessProxyImpl.KEY.get(processHandler);
  }
}
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.SystemInfo;

import java.io.File;

public class ProcessProxyFactoryImpl extends ProcessProxyFactory {
  private static final String DONT_USE_LAUNCHER_PROPERTY = "idea.no.launcher";

  @Override
  public ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException {
    JavaParameters javaParameters = javaCmdLine.getJavaParameters();
    if (useLauncher(javaParameters)) {
      String mainClass = javaParameters.getMainClass();
      if (mainClass != null) {
        try {
          ProcessProxyImpl proxy = new ProcessProxyImpl();
          JavaSdkUtil.addRtJar(javaParameters.getClassPath());
          ParametersList vmParametersList = javaParameters.getVMParametersList();
          vmParametersList.defineProperty(ProcessProxyImpl.PROPERTY_PORT_NUMBER, String.valueOf(proxy.getPortNumber()));
          vmParametersList.defineProperty(ProcessProxyImpl.PROPERTY_BIN_PATH, PathManager.getBinPath());
          javaParameters.getProgramParametersList().prepend(mainClass);
          javaParameters.setMainClass(ProcessProxyImpl.LAUNCH_MAIN_CLASS);
          return proxy;
        }
        catch (ProcessProxyImpl.NoMoreSocketsException ignored) { }
      }
    }

    return null;
  }

  @Override
  public ProcessProxy getAttachedProxy(ProcessHandler processHandler) {
    return processHandler != null ? processHandler.getUserData(ProcessProxyImpl.KEY) : null;
  }

  @Override
  public boolean isBreakGenLibraryAvailable() {
    String libName;
    if (SystemInfo.isWindows) {
      libName = "breakgen.dll";
    }
    else if (SystemInfo.isMac) {
      libName = "libbreakgen.jnilib";
    }
    else {
      libName = "libbreakgen.so";
    }
    return new File(PathManager.getBinPath(), libName).exists();
  }

  private static boolean useLauncher(JavaParameters parameters) {
    return !Boolean.getBoolean(DONT_USE_LAUNCHER_PROPERTY) && parameters.getModuleName() == null;
  }
}
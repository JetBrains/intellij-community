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
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class ProcessProxyFactoryImpl extends ProcessProxyFactory {
  public ProcessProxy createCommandLineProxy(final JavaCommandLine javaCmdLine) throws ExecutionException {
    ProcessProxyImpl proxy = null;
    final JavaParameters javaParameters = javaCmdLine.getJavaParameters();
    String mainClass = javaParameters.getMainClass();
    if (ProcessProxyImpl.useLauncher() && mainClass != null) {
      try {
        proxy = new ProcessProxyImpl();
        JavaSdkUtil.addRtJar(javaParameters.getClassPath());
        final ParametersList vmParametersList = javaParameters.getVMParametersList();
        vmParametersList.defineProperty(ProcessProxyImpl.PROPERTY_PORT_NUMBER, String.valueOf(proxy.getPortNumber()));
        vmParametersList.defineProperty(ProcessProxyImpl.PROPERTY_BINPATH, PathManager.getBinPath());
        javaParameters.getProgramParametersList().prepend(mainClass);
        javaParameters.setMainClass(ProcessProxyImpl.LAUNCH_MAIN_CLASS);
      }
      catch (ProcessProxyImpl.NoMoreSocketsException e) {
        proxy = null;
      }
    }
    return proxy;
  }

  public ProcessProxy getAttachedProxy(final ProcessHandler processHandler) {
    return processHandler != null ? processHandler.getUserData(ProcessProxyImpl.KEY) : null;
  }

  @Override
  public boolean isBreakGenLibraryAvailable() {
    @NonNls final String libName;
    if (SystemInfo.isWindows) {
      libName = "breakgen.dll";
    }
    else if (SystemInfo.isMac) {
      libName = "libbreakgen.jnilib";
    }
    else {
      libName = "libbreakgen.so";
    }
    return new File(PathManager.getBinPath() + File.separator + libName).exists();
  }
}
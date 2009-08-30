/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.application.PathManager;

public class ProcessProxyFactoryImpl extends ProcessProxyFactory {
  public ProcessProxy createCommandLineProxy(final JavaCommandLine javaCmdLine) throws ExecutionException {
    ProcessProxyImpl proxy = null;
    if (ProcessProxyImpl.useLauncher()) {
      try {
        proxy = new ProcessProxyImpl();
        final JavaParameters javaParameters = javaCmdLine.getJavaParameters();
        JavaSdkUtil.addRtJar(javaParameters.getClassPath());
        final ParametersList vmParametersList = javaParameters.getVMParametersList();
        vmParametersList.defineProperty(ProcessProxyImpl.PROPERTY_PORT_NUMBER, "" + proxy.getPortNumber());
        vmParametersList.defineProperty(ProcessProxyImpl.PROPERTY_BINPATH, PathManager.getBinPath());
        javaParameters.getProgramParametersList().prepend(javaParameters.getMainClass());
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
}
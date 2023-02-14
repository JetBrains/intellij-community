// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMainV2;

import java.io.File;

public class ProcessProxyFactoryImpl extends ProcessProxyFactory {
  private static final boolean ourMayUseLauncher = !Boolean.getBoolean("idea.no.launcher");

  @Override
  public ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException {
    JavaParameters javaParameters = javaCmdLine.getJavaParameters();
    String mainClass = javaParameters.getMainClass();

    if (ourMayUseLauncher && mainClass != null) {
      String rtJarPath = JavaSdkUtil.getIdeaRtJarPath();
      boolean runtimeJarFile = new File(rtJarPath).isFile() && FileUtil.isAncestor(PathManager.getHomePath(), rtJarPath, true);

      if (runtimeJarFile || javaParameters.getModuleName() == null) {
        try {
          ProcessProxyImpl proxy = new ProcessProxyImpl(StringUtil.getShortName(mainClass));
          String port = String.valueOf(proxy.getPortNumber());
          String binPath = proxy.getBinPath();
          JavaSdkVersion jdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(javaParameters.getJdk());
          if (jdkVersion != null && !jdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7)) {
            throw new ExecutionException(JavaBundle.message("error.message.ide.does.not.support.starting.processes.using.old.java", 
                                                            jdkVersion.getDescription()));
          }

          if (runtimeJarFile) {
            javaParameters.getVMParametersList().add("-javaagent:" + rtJarPath + '=' + port + ':' + binPath);
          }
          else {
            JavaSdkUtil.addRtJar(javaParameters.getClassPath());

            ParametersList vmParametersList = javaParameters.getVMParametersList();
            vmParametersList.defineProperty(AppMainV2.LAUNCHER_PORT_NUMBER, port);
            vmParametersList.defineProperty(AppMainV2.LAUNCHER_BIN_PATH, binPath);

            javaParameters.getProgramParametersList().prepend(mainClass);
            javaParameters.setMainClass(AppMainV2.class.getName());
          }

          return proxy;
        }
        catch (ExecutionException e) {
          throw e;
        }
        catch (Exception e) {
          Logger.getInstance(ProcessProxy.class).warn(e);
        }
      }
    }

    return null;
  }

  @Override
  public ProcessProxy getAttachedProxy(ProcessHandler processHandler) {
    return ProcessProxyImpl.KEY.get(processHandler);
  }
}
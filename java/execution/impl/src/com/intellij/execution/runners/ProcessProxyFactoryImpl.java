// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMainV2;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProcessProxyFactoryImpl extends ProcessProxyFactory {
  private static final boolean ourMayUseLauncher = !Boolean.getBoolean("idea.no.launcher");

  @Override
  public ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException {
    JavaParameters javaParameters = javaCmdLine.getJavaParameters();
    String mainClass = javaParameters.getMainClass();

    if (ourMayUseLauncher && mainClass != null) {
      var rtJarPath = Path.of(JavaSdkUtil.getIdeaRtJarPath());
      var runtimeJarFile = Files.isRegularFile(rtJarPath) && rtJarPath.startsWith(PathManager.getHomePath());

      if (runtimeJarFile || javaParameters.getModuleName() == null) {
        try {
          ProcessProxyImpl proxy = new ProcessProxyImpl(StringUtil.getShortName(mainClass));
          String port = String.valueOf(proxy.getPortNumber());
          JavaSdkVersion jdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(javaParameters.getJdk());
          if (jdkVersion != null && !jdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8)) {
            throw new ExecutionException(JavaBundle.message("error.message.ide.does.not.support.starting.processes.using.old.java.app",
                                                            jdkVersion.getDescription()));
          }

          if (runtimeJarFile) {
            javaParameters.getVMParametersList().add("-javaagent:" + rtJarPath + '=' + port);
          }
          else {
            JavaSdkUtil.addRtJar(javaParameters.getClassPath());

            ParametersList vmParametersList = javaParameters.getVMParametersList();
            vmParametersList.defineProperty(AppMainV2.LAUNCHER_PORT_NUMBER, port);

            boolean isJava21preview = JavaSdkVersion.JDK_21.equals(jdkVersion) &&
                                      javaParameters.getVMParametersList().getParameters().contains(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
            if (isJava21preview) {
              vmParametersList.defineProperty(AppMainV2.LAUNCHER_USE_JDK_21_PREVIEW, Boolean.toString(isJava21preview));
            }

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

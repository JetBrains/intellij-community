// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.AsyncStacksUtils;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class RemoteConnectionBuilder {
  private static final Logger LOG = Logger.getInstance(RemoteConnectionBuilder.class);

  private final int myTransport;
  private final boolean myServer;
  private final String myAddress;
  private boolean myCheckValidity;
  private boolean myAsyncAgent;
  private boolean myQuiet;
  private boolean mySuspend = true;
  private Project myProject;
  private boolean myMatchWithExecutionTarget = false;

  public RemoteConnectionBuilder(boolean server, int transport, String address) {
    myTransport = transport;
    myServer = server;
    myAddress = address;
  }

  public RemoteConnectionBuilder checkValidity(boolean check) {
    myCheckValidity = check;
    return this;
  }

  public RemoteConnectionBuilder asyncAgent(boolean useAgent) {
    myAsyncAgent = useAgent;
    return this;
  }

  public RemoteConnectionBuilder project(Project project) {
    myProject = project;
    return this;
  }

  public RemoteConnectionBuilder quiet() {
    myQuiet = true;
    return this;
  }

  public RemoteConnectionBuilder suspend(boolean suspend) {
    mySuspend = suspend;
    return this;
  }

  /**
   * The created set of JavaParameters would be prepared for execution on a target that match to the project.
   * All used paths would be mapped for the correct execution target via EEL Api based on the location of the project.
   */
  public RemoteConnectionBuilder matchWithExecutionTarget() {
    myMatchWithExecutionTarget = true;
    return this;
  }

  public RemoteConnection create(JavaParameters parameters) throws ExecutionException {
    if (myCheckValidity) {
      checkTargetJPDAInstalled(parameters);
    }

    final boolean useSockets = myTransport == DebuggerSettings.SOCKET_TRANSPORT;

    String address = "";
    if (StringUtil.isEmptyOrSpaces(myAddress)) {
      try {
        address = DebuggerUtils.getInstance().findAvailableDebugAddress(useSockets);
      }
      catch (ExecutionException e) {
        if (myCheckValidity) {
          throw e;
        }
      }
    }
    else {
      address = myAddress;
    }

    final String debugAddress = myServer && useSockets ? DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK + ":" + address : address;
    StringBuilder debuggeeRunProperties = new StringBuilder();
    debuggeeRunProperties.append("transport=").append(DebugProcessImpl.findConnector(useSockets, myServer).transport().name());
    debuggeeRunProperties.append(",address=").append(debugAddress);
    debuggeeRunProperties.append(mySuspend ? ",suspend=y" : ",suspend=n");
    debuggeeRunProperties.append(myServer ? ",server=n" : ",server=y");

    if (StringUtil.containsWhitespaces(debuggeeRunProperties)) {
      debuggeeRunProperties.append("\"").append(debuggeeRunProperties).append("\"");
    }

    if (myQuiet) {
      debuggeeRunProperties.append(",quiet=y");
    }

    if (Registry.is("debugger.jdwp.include.virtual.threads")) {
      debuggeeRunProperties.append(",includevirtualthreads=y");
    }

    final String _debuggeeRunProperties = debuggeeRunProperties.toString();

    ApplicationManager.getApplication().runReadAction(() -> {
      addRtJar(parameters.getClassPath());

      if (myAsyncAgent) {
        AsyncStacksUtils.addDebuggerAgent(parameters, myProject, true, null, myMatchWithExecutionTarget);
      }

      if (DebuggerSettings.getInstance().ENABLE_MEMORY_AGENT) {
        var version = JavaSdkVersionUtil.getJavaSdkVersion(parameters.getJdk());
        // It's dangerous to set VM options for unknown JDK, so we check for null explicitly,
        // it's better to have a warning rather than inability to start JVM.
        if (version != null && version.isAtLeast(JavaSdkVersion.JDK_24)) {
          var p = "--enable-native-access=ALL-UNNAMED";
          parameters.getVMParametersList().replaceOrPrepend(p, p);
        }
      }

      parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "");
      parameters.getVMParametersList().replaceOrPrepend("-agentlib:jdwp=", "-agentlib:jdwp=" + _debuggeeRunProperties);
    });

    return new RemoteConnection(useSockets, DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK, address, myServer);
  }

  private static void addRtJar(@NotNull PathsList pathsList) {
    if (Registry.is("debugger.add.rt.jar", true)) {
      if (PluginManagerCore.isRunningFromSources()) {
        // When running from sources, rt.jar from sources should be preferred
        String path = DebuggerUtilsImpl.getIdeaRtPath();
        pathsList.remove(JavaSdkUtil.getIdeaRtJarPath());
        pathsList.addTail(path);
        LOG.debug("Running from sources IDE, add rt.jar: " + path);
      }
      else {
        // Works for IDEA/dev build/test configurations
        boolean isIdeaBuild = ContainerUtil.exists(pathsList.getPathList(), p -> p.contains("intellij.java.rt"));
        if (isIdeaBuild) {
          // When building IDEA itself, idea_rt.jar should not be taken from sources,
          // as IDEA expects to use the matching idea_rt.jar from the installation dir.
          String path = JavaSdkUtil.getIdeaRtJarPath();
          pathsList.addFirst(path);
          LOG.debug("Running IDEA from release IDE, add rt.jar: " + path);
        }
        else {
          JavaSdkUtil.addRtJar(pathsList);
          LOG.debug("Running from release IDE, add rt.jar");
        }
      }
    }
  }

  private static void checkTargetJPDAInstalled(@NotNull JavaParameters parameters) throws ExecutionException {
    if (parameters.getJdk() == null) {
      throw new ExecutionException(JavaDebuggerBundle.message("error.jdk.not.specified"));
    }
  }
}

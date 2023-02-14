// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.AsyncStacksUtils;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.settings.CaptureSettingsProvider;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.BuildDependenciesJps;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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

    final String _debuggeeRunProperties = debuggeeRunProperties.toString();

    ApplicationManager.getApplication().runReadAction(() -> {
      addRtJar(parameters.getClassPath());

      if (myAsyncAgent) {
        addDebuggerAgent(parameters, myProject);
      }

      final boolean forceNoJIT = DebuggerSettings.getInstance().DISABLE_JIT;
      final String debugKey = System.getProperty(DEBUG_KEY_NAME, "-Xdebug");
      final boolean needDebugKey = forceNoJIT || !"-Xdebug".equals(debugKey) /*the key is non-standard*/;

      if (forceNoJIT || needDebugKey) {
        parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "-Xrunjdwp:" + _debuggeeRunProperties);
      }
      else {
        // use newer JVMTI if available
        parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "");
        parameters.getVMParametersList().replaceOrPrepend("-agentlib:jdwp=", "-agentlib:jdwp=" + _debuggeeRunProperties);
      }

      if (forceNoJIT) {
        parameters.getVMParametersList().replaceOrPrepend("-Djava.compiler=", "-Djava.compiler=NONE");
        parameters.getVMParametersList().replaceOrPrepend("-Xnoagent", "-Xnoagent");
      }

      if (needDebugKey) {
        parameters.getVMParametersList().replaceOrPrepend(debugKey, debugKey);
      }
      else {
        // deliberately skip outdated parameter because it can disable full-speed debugging for some jdk builds
        // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6272174
        parameters.getVMParametersList().replaceOrPrepend("-Xdebug", "");
      }
    });

    return new RemoteConnection(useSockets, DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK, address, myServer);
  }

  private static void addRtJar(@NotNull PathsList pathsList) {
    if (PluginManagerCore.isRunningFromSources()) {
      String path = DebuggerUtilsImpl.getIdeaRtPath();
      pathsList.remove(JavaSdkUtil.getIdeaRtJarPath());
      pathsList.addTail(path);
    }
    else {
      JavaSdkUtil.addRtJar(pathsList);
    }
  }

  private static void checkTargetJPDAInstalled(@NotNull JavaParameters parameters) throws ExecutionException {
    if (parameters.getJdk() == null) {
      throw new ExecutionException(JavaDebuggerBundle.message("error.jdk.not.specified"));
    }
  }

  private static final String AGENT_JAR_NAME = "debugger-agent.jar";
  private static final String DEBUG_KEY_NAME = "idea.xdebug.key";

  private static void addDebuggerAgent(JavaParameters parameters, @Nullable Project project) {
    if (AsyncStacksUtils.isAgentEnabled()) {
      String prefix = "-javaagent:";
      ParametersList parametersList = parameters.getVMParametersList();
      if (!ContainerUtil.exists(parametersList.getParameters(), p -> p.startsWith(prefix) && p.contains(AGENT_JAR_NAME))) {
        Sdk jdk = parameters.getJdk();
        if (jdk != null) {
          JavaSdkVersion sdkVersion = JavaSdk.getInstance().getVersion(jdk);
          if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7)) {
            Path agentArtifactPath;

            Path classesRoot = Path.of(PathUtil.getJarPathForClass(DebuggerManagerImpl.class));
            // isDirectory(classesRoot) is used instead of `PluginManagerCore.isRunningFromSources()`
            // because we want to use installer's layout when running "IDEA (dev build)" run configuration
            // where the layout is quite the same as in installers.
            // but `PluginManagerCore.isRunningFromSources()` still returns `true` in this case
            if (Files.isDirectory(classesRoot)) {
              // Code runs from IDEA run configuration (code from .class file in out/ directory)
              try {
                // The agent file must have a fixed name (AGENT_JAR_NAME) which is mentioned in MANIFEST.MF inside
                Path debuggerAgentDir =
                  FileUtil.createTempDirectory(new File(PathManager.getTempPath()), "debugger-agent", "", true).toPath();
                agentArtifactPath = debuggerAgentDir.resolve(AGENT_JAR_NAME);

                Path communityRoot = Path.of(PathManager.getCommunityHomePath());
                Path iml = BuildDependenciesJps.getProjectModule(communityRoot, "intellij.java.debugger.agent.holder");
                Path downloadedAgent = BuildDependenciesJps.getModuleLibrarySingleRoot(
                  iml,
                  "debugger-agent",
                  BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
                  new BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath())));

                Files.copy(downloadedAgent, agentArtifactPath);
              }
              catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
            else {
              agentArtifactPath = classesRoot.resolveSibling("rt").resolve(AGENT_JAR_NAME);
            }

            if (Files.exists(agentArtifactPath)) {
              String agentPath = JavaExecutionUtil.handleSpacesInAgentPath(agentArtifactPath.toAbsolutePath().toString(),
                                                                           "captureAgent", null,
                                                                           f -> f.getName().startsWith("debugger-agent"));
              if (agentPath != null) {
                parametersList.add(prefix + agentPath + generateAgentSettings(project));
              }
            }
            else {
              LOG.error("Capture agent not found: " + agentArtifactPath);
            }
          }
          else {
            LOG.warn("Capture agent is not supported for JRE " + sdkVersion);
          }
        }
      }
    }
  }

  private static String generateAgentSettings(@Nullable Project project) {
    Properties properties = CaptureSettingsProvider.getPointsProperties(project);
    if (!properties.isEmpty()) {
      try {
        File file = FileUtil.createTempFile("capture", ".props");
        try (FileOutputStream out = new FileOutputStream(file)) {
          properties.store(out, null);
          return "=" + file.toURI().toASCIIString();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return "";
  }
}

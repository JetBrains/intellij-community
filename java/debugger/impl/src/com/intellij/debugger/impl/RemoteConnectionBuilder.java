// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.AsyncStacksUtils;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.memory.agent.MemoryAgentUtil;
import com.intellij.debugger.settings.CaptureSettingsProvider;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.GetJPDADialog;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.jar.Attributes;

/**
 * @author egor
 */
public class RemoteConnectionBuilder {
  private static final Logger LOG = Logger.getInstance(RemoteConnectionBuilder.class);
  
  private final int myTransport;
  private final boolean myServer;
  private final String myAddress;
  private boolean myCheckValidity;
  private boolean myAsyncAgent;
  private boolean myMemoryAgent;
  private boolean myQuiet;

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

  public RemoteConnectionBuilder memoryAgent(boolean useAgent) {
    myMemoryAgent = useAgent;
    return this;
  }

  public RemoteConnectionBuilder quiet() {
    myQuiet = true;
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
    String debuggeeRunProperties =
      "transport=" + DebugProcessImpl.findConnector(useSockets, myServer).transport().name() + ",address=" + debugAddress;
    if (myServer) {
      debuggeeRunProperties += ",suspend=y,server=n";
    }
    else {
      debuggeeRunProperties += ",suspend=n,server=y";
    }

    if (StringUtil.containsWhitespaces(debuggeeRunProperties)) {
      debuggeeRunProperties = "\"" + debuggeeRunProperties + "\"";
    }

    if (myQuiet) {
      debuggeeRunProperties += ",quiet=y";
    }

    final String _debuggeeRunProperties = debuggeeRunProperties;

    ApplicationManager.getApplication().runReadAction(() -> {
      JavaSdkUtil.addRtJar(parameters.getClassPath());

      if (myAsyncAgent) {
        addDebuggerAgent(parameters);
      }

      if (myMemoryAgent) {
        MemoryAgentUtil.addMemoryAgent(parameters);
      }

      final Sdk jdk = parameters.getJdk();
      final boolean forceClassicVM = shouldForceClassicVM(jdk);
      final boolean forceNoJIT = shouldForceNoJIT(jdk);
      final String debugKey = System.getProperty(DEBUG_KEY_NAME, "-Xdebug");
      final boolean needDebugKey = shouldAddXdebugKey(jdk) || !"-Xdebug".equals(debugKey) /*the key is non-standard*/;

      if (forceClassicVM || forceNoJIT || needDebugKey || !isJVMTIAvailable(jdk)) {
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

      parameters.getVMParametersList().replaceOrPrepend("-classic", forceClassicVM ? "-classic" : "");
    });

    return new RemoteConnection(useSockets, DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK, address, myServer);
  }

  private static void checkTargetJPDAInstalled(JavaParameters parameters) throws ExecutionException {
    final Sdk jdk = parameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(DebuggerBundle.message("error.jdk.not.specified"));
    }
    final JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    if (version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1) {
      String versionString = jdk.getVersionString();
      throw new ExecutionException(DebuggerBundle.message("error.unsupported.jdk.version", versionString));
    }
    if (SystemInfo.isWindows && version == JavaSdkVersion.JDK_1_2) {
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null || !homeDirectory.isValid()) {
        String versionString = jdk.getVersionString();
        throw new ExecutionException(DebuggerBundle.message("error.invalid.jdk.home", versionString));
      }
      File dllFile = new File(
        homeDirectory.getPath().replace('/', File.separatorChar) + File.separator + "bin" + File.separator + "jdwp.dll"
      );
      if (!dllFile.exists()) {
        GetJPDADialog dialog = new GetJPDADialog();
        dialog.show();
        throw new ExecutionException(DebuggerBundle.message("error.debug.libraries.missing"));
      }
    }
  }

  private static final String AGENT_FILE_NAME = "debugger-agent.jar";
  @NonNls private static final String DEBUG_KEY_NAME = "idea.xdebug.key";

  private static void addDebuggerAgent(JavaParameters parameters) {
    if (AsyncStacksUtils.isAgentEnabled()) {
      String prefix = "-javaagent:";
      ParametersList parametersList = parameters.getVMParametersList();
      if (parametersList.getParameters().stream().noneMatch(p -> p.startsWith(prefix) && p.contains(AGENT_FILE_NAME))) {
        Sdk jdk = parameters.getJdk();
        String version = jdk != null ? JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION) : null;
        if (version != null) {
          JavaSdkVersion sdkVersion = JavaSdkVersion.fromVersionString(version);
          if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_6)) {
            File classesRoot = new File(PathUtil.getJarPathForClass(DebuggerManagerImpl.class));
            File agentFile;
            if (classesRoot.isFile()) {
              agentFile = new File(classesRoot.getParentFile(), "rt/" + AGENT_FILE_NAME);
            }
            else {
              File artifactsInBuildScripts = new File(classesRoot.getParentFile().getParentFile().getParentFile(), "project-artifacts");
              if (artifactsInBuildScripts.exists()) {
                //running tests via build scripts
                agentFile = new File(artifactsInBuildScripts, "debugger_agent/" + AGENT_FILE_NAME);
              }
              else {
                //running IDE or tests in IDE
                agentFile = new File(classesRoot.getParentFile().getParentFile(), "/artifacts/debugger_agent/" + AGENT_FILE_NAME);
              }
            }
            if (agentFile.exists()) {
              String agentPath = JavaExecutionUtil.handleSpacesInAgentPath(
                agentFile.getAbsolutePath(), "captureAgent", null, f -> f.getName().startsWith("debugger-agent"));
              if (agentPath != null) {
                parametersList.add(prefix + agentPath + generateAgentSettings());
              }
            }
            else {
              LOG.warn("Capture agent not found: " + agentFile);
            }
          }
          else {
            LOG.warn("Capture agent is not supported for jre " + version);
          }
        }
      }
    }
  }

  private static String generateAgentSettings() {
    Properties properties = CaptureSettingsProvider.getPointsProperties();
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

  /**
   * for Target JDKs versions 1.2.x - 1.3.0 the Classic VM should be used for debugging
   */
  private static boolean shouldForceClassicVM(Sdk jdk) {
    if (SystemInfo.isMac) {
      return false;
    }
    if (jdk == null) return false;

    String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    if (version == null || StringUtil.compareVersionNumbers(version, "1.4") >= 0) {
      return false;
    }

    if (version.startsWith("1.2") && SystemInfo.isWindows) {
      return true;
    }
    version += ".0";
    if (version.startsWith("1.3.0") && SystemInfo.isWindows) {
      return true;
    }
    if ((version.startsWith("1.3.1_07") || version.startsWith("1.3.1_08")) && SystemInfo.isWindows) {
      return false; // fixes bug for these JDKs that it cannot start with -classic option
    }
    return DebuggerSettings.getInstance().FORCE_CLASSIC_VM;
  }

  private static boolean shouldForceNoJIT(Sdk jdk) {
    if (DebuggerSettings.getInstance().DISABLE_JIT) {
      return true;
    }
    if (jdk != null) {
      final String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
      if (version != null && (version.startsWith("1.2") || version.startsWith("1.3"))) {
        return true;
      }
    }
    return false;
  }

  private static boolean shouldAddXdebugKey(Sdk jdk) {
    if (jdk == null) {
      return true; // conservative choice
    }
    if (DebuggerSettings.getInstance().DISABLE_JIT) {
      return true;
    }

    //if (ApplicationManager.getApplication().isUnitTestMode()) {
    // need this in unit tests to avoid false alarms when comparing actual output with expected output
    //return true;
    //}

    final String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    return version == null ||
           //version.startsWith("1.5") ||
           version.startsWith("1.4") ||
           version.startsWith("1.3") ||
           version.startsWith("1.2") ||
           version.startsWith("1.1") ||
           version.startsWith("1.0");
  }

  private static boolean isJVMTIAvailable(Sdk jdk) {
    if (jdk == null) {
      return false; // conservative choice
    }

    final String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    if (version == null) {
      return false;
    }
    return !(version.startsWith("1.4") ||
             version.startsWith("1.3") ||
             version.startsWith("1.2") ||
             version.startsWith("1.1") ||
             version.startsWith("1.0"));
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMainV2;
import com.intellij.util.ArrayUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.xdebugger.attach.XDefaultLocalAttachGroup;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author egor
 */
public class JavaAttachDebuggerProvider implements XLocalAttachDebuggerProvider {
  private static final Logger LOG = Logger.getInstance(JavaAttachDebuggerProvider.class);

  private static class JavaLocalAttachDebugger implements XLocalAttachDebugger {
    private final Project myProject;
    private final LocalAttachInfo myInfo;

    public JavaLocalAttachDebugger(@NotNull Project project, @NotNull LocalAttachInfo info) {
      myProject = project;
      myInfo = info;
    }

    @NotNull
    @Override
    public String getDebuggerDisplayName() {
      return "Java Debugger";
    }

    @Override
    public void attachDebugSession(@NotNull Project project, @NotNull ProcessInfo processInfo) {
      attach(myInfo, myProject);
    }
  }

  private static final Key<Map<String, LocalAttachInfo>> ADDRESS_MAP_KEY = Key.create("ADDRESS_MAP");

  private static final XLocalAttachGroup ourAttachGroup = new XDefaultLocalAttachGroup() {
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
    public String getProcessDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      LocalAttachInfo attachInfo = getAttachInfo(info, dataHolder.getUserData(ADDRESS_MAP_KEY));
      assert attachInfo != null;
      String res = "";
      String executable = info.getExecutableDisplayName();
      if ("java".equals(executable)) {
        if (!StringUtil.isEmpty(attachInfo.myClass)) {
          res = attachInfo.myClass;
        }
        else {
          res = StringUtil.notNullize(ArrayUtil.getLastElement(info.getCommandLine().split(" "))); // should be class name
        }
      }
      else {
        res = executable;
      }
      return attachInfo.getProcessDisplayText(res);
    }
  };

  @NotNull
  @Override
  public XLocalAttachGroup getAttachGroup() {
    return ourAttachGroup;
  }

  @NotNull
  @Override
  public List<XLocalAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                          @NotNull ProcessInfo processInfo,
                                                          @NotNull UserDataHolder contextHolder) {
    Map<String, LocalAttachInfo> addressMap = contextHolder.getUserData(ADDRESS_MAP_KEY);
    if (addressMap == null) {
      addressMap = new HashMap<>();
      contextHolder.putUserData(ADDRESS_MAP_KEY, addressMap);
      final Map<String, LocalAttachInfo> map = addressMap;
      VirtualMachine.list().forEach(desc -> {
        LocalAttachInfo address = getProcessAttachInfo(desc.id());
        if (address != null) {
          map.put(desc.id(), address);
        }
      });
    }

    LocalAttachInfo info = getAttachInfo(processInfo, addressMap);
    return info != null ? Collections.singletonList(new JavaLocalAttachDebugger(project, info)) : Collections.emptyList();
  }

  @Nullable
  private static LocalAttachInfo getAttachInfo(ProcessInfo processInfo, @Nullable Map<String, LocalAttachInfo> addressMap) {
    LocalAttachInfo res;
    String pidString = String.valueOf(processInfo.getPid());
    if (addressMap != null) {
      res = addressMap.get(pidString);
    }
    else {
      res = getProcessAttachInfo(pidString);
    }
    if (res == null) {
      res = getProcessAttachInfo(ParametersListUtil.parse(processInfo.getCommandLine()), pidString);
    }
    return res;
  }

  @Nullable
  static LocalAttachInfo getProcessAttachInfo(List<String> arguments, String pid) {
    String address;
    boolean socket;
    for (String argument : arguments) {
      if (argument.startsWith("-agentlib:jdwp") &&
          argument.contains("server=y") &&
          (argument.contains("transport=dt_shmem") || argument.contains("transport=dt_socket"))) {
        socket = argument.contains("transport=dt_socket");
        String[] params = argument.split(",");
        for (String param : params) {
          if (param.startsWith("address")) {
            try {
              address = param.split("=")[1];
              return new DebuggerLocalAttachInfo(socket, address, null, pid);
            }
            catch (Exception e) {
              LOG.error(e);
              return null;
            }
          }
        }
        break;
      }
    }
    return null;
  }

  @Nullable
  static LocalAttachInfo getProcessAttachInfo(String pid) {
    VirtualMachine vm = null;
    try {
      vm = VirtualMachine.attach(pid);
      Properties agentProperties = vm.getAgentProperties();
      String command = agentProperties.getProperty("sun.java.command");
      if (!StringUtil.isEmpty(command)) {
        command = StringUtil.replace(command, AppMainV2.class.getName(), "").trim();
        command = StringUtil.substringBefore(command, " ");
      }
      String property = agentProperties.getProperty("sun.jdwp.listenerAddress");
      if (property != null && property.indexOf(':') != -1) {
        return new DebuggerLocalAttachInfo(!"dt_shmem".equals(StringUtil.substringBefore(property, ":")),
                                           StringUtil.substringAfter(property, ":"),
                                           command,
                                           pid);
      }

      // sa pid attach if sa-jdi.jar is available
      if (SAPidRemoteConnection.isSAPidAttachAvailable()) {
        Properties systemProperties = vm.getSystemProperties();
        File saJdiJar = new File(systemProperties.getProperty("java.home"), "../lib/sa-jdi.jar"); // java 8 only for now
        if (saJdiJar.exists()) {
          return new LocalAttachInfo(command, pid, saJdiJar.getCanonicalPath());
        }
      }
    }
    catch (AttachNotSupportedException | InternalError | IOException ignored) {
    }
    finally {
      if (vm != null) {
        try {
          vm.detach();
        }
        catch (IOException ignored) {
        }
      }
    }
    return null;
  }

  private static class DebuggerLocalAttachInfo extends LocalAttachInfo {
    final boolean myUseSocket;
    final String myAddress;

    public DebuggerLocalAttachInfo(boolean socket, @NotNull String address, String aClass, String pid) {
      super(aClass, pid, "");
      myUseSocket = socket;
      myAddress = address;
    }

    @Override
    RemoteConnection createConnection() {
      return new RemoteConnection(myUseSocket, DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK, myAddress, false);
    }

    @Override
    String getSessionName() {
      return "localhost:" + myAddress;
    }

    @Override
    String getProcessDisplayText(String text) {
      return text + " (" + myAddress + ")";
    }
  }

  static class LocalAttachInfo {
    final String myClass;
    final String myPid;
    final String mySAJarPath;

    private LocalAttachInfo(String aClass, String pid, String SAJarPath) {
      myClass = aClass;
      myPid = pid;
      mySAJarPath = SAJarPath;
    }

    RemoteConnection createConnection() {
      return new SAPidRemoteConnection(myPid, mySAJarPath);
    }

    String getSessionName() {
      return "pid " + myPid;
    }

    String getProcessDisplayText(String text) {
      return text + " (read only)";
    }
  }

  private static class ProcessAttachDebugExecutor extends DefaultDebugExecutor {
    static ProcessAttachDebugExecutor INSTANCE = new ProcessAttachDebugExecutor();

    private ProcessAttachDebugExecutor() {
    }

    @NotNull
    @Override
    public String getId() {
      return "ProcessAttachDebug";
    }
  }

  public static class ProcessAttachDebuggerRunner extends GenericDebuggerRunner {
    @NotNull
    @Override
    public String getRunnerId() {
      return "ProcessAttachDebuggerRunner";
    }

    @Nullable
    @Override
    protected RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
      throws ExecutionException {
      return attachVirtualMachine(state, environment, ((RemoteState)state).getRemoteConnection(), false);
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
      return executorId.equals(ProcessAttachDebugExecutor.INSTANCE.getId());
    }
  }

  private static class ProcessAttachRunConfiguration extends RunConfigurationBase {
    private LocalAttachInfo myAttachInfo;

    protected ProcessAttachRunConfiguration(@NotNull Project project) {
      super(project, ProcessAttachRunConfigurationType.FACTORY, "ProcessAttachRunConfiguration");
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      throw new IllegalStateException("Editing is not supported");
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
      return new RemoteStateState(getProject(), myAttachInfo.createConnection());
    }
  }

  private static class ProcessAttachRunConfigurationType implements ConfigurationType {
    static final ProcessAttachRunConfigurationType INSTANCE = new ProcessAttachRunConfigurationType();
    static final ConfigurationFactory FACTORY = new ConfigurationFactory(INSTANCE) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new ProcessAttachRunConfiguration(project);
      }
    };

    @Nls
    @Override
    public String getDisplayName() {
      return getId();
    }

    @Nls
    @Override
    public String getConfigurationTypeDescription() {
      return getId();
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @NotNull
    @Override
    public String getId() {
      return "ProcessAttachRunConfigurationType";
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
      return new ConfigurationFactory[]{FACTORY};
    }
  }

  static void attach(JavaAttachDebuggerProvider.LocalAttachInfo info, Project project) {
    RunnerAndConfigurationSettings runSettings =
      RunManager
        .getInstance(project).createRunConfiguration(info.getSessionName(), JavaAttachDebuggerProvider.ProcessAttachRunConfigurationType.FACTORY);
    ((JavaAttachDebuggerProvider.ProcessAttachRunConfiguration)runSettings.getConfiguration()).myAttachInfo = info;
    ProgramRunnerUtil.executeConfiguration(runSettings, JavaAttachDebuggerProvider.ProcessAttachDebugExecutor.INSTANCE);
  }
}

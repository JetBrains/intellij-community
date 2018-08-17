// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMainV2;
import com.intellij.util.ArrayUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.xdebugger.attach.XDefaultLocalAttachGroup;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import com.sun.jdi.connect.Connector;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author egor
 */
public class JavaAttachDebuggerProvider implements XLocalAttachDebuggerProvider {
  private static final boolean SA_PID_ATTACH_AVAILABLE;

  static {
    Connector sapidAttachConnector = null;
    try {
      sapidAttachConnector = DebugProcessImpl.findConnector(DebugProcessImpl.SAPID_ATTACHING_CONNECTOR_NAME);
    }
    catch (ExecutionException ignored) {
    }
    SA_PID_ATTACH_AVAILABLE = sapidAttachConnector != null;
  }

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
      RunnerAndConfigurationSettings runSettings =
        RunManager.getInstance(myProject).createRunConfiguration(myInfo.getSessionName(), ProcessAttachRunConfigurationType.FACTORY);
      ((ProcessAttachRunConfiguration)runSettings.getConfiguration()).myAttachInfo = myInfo;
      ProgramRunnerUtil.executeConfiguration(runSettings, ProcessAttachDebugExecutor.INSTANCE);
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
      Pair<String, Integer> address = DebugAttachDetector.getAttachAddress(ParametersListUtil.parse(processInfo.getCommandLine()));
      if (address != null) {
        res = new DebuggerLocalAttachInfo(true, String.valueOf(address.getSecond()), null, pidString);
      }
    }
    return res;
  }

  @Nullable
  private static LocalAttachInfo getProcessAttachInfo(String pid) {
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
      if (SA_PID_ATTACH_AVAILABLE) {
        return new LocalAttachInfo(command, pid);
      }
    }
    catch (AttachNotSupportedException | IOException ignored) {
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
      super(aClass, pid);
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

  private static class LocalAttachInfo {
    final String myClass;
    final String myPid;

    private LocalAttachInfo(String aClass, String pid) {
      myClass = aClass;
      myPid = pid;
    }

    RemoteConnection createConnection() {
      return new PidRemoteConnection(myPid);
    }

    String getSessionName() {
      return "pid " + myPid;
    }

    String getProcessDisplayText(String text) {
      return text;
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
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMainV2;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.xdebugger.attach.*;
import com.jetbrains.sa.SaJdwp;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class JavaAttachDebuggerProvider implements XAttachDebuggerProvider {
  private static final Logger LOG = Logger.getInstance(JavaAttachDebuggerProvider.class);

  private static class JavaLocalAttachDebugger implements XLocalAttachDebugger {
    private final Project myProject;
    private final LocalAttachInfo myInfo;

    JavaLocalAttachDebugger(@NotNull Project project, @NotNull LocalAttachInfo info) {
      myProject = project;
      myInfo = info;
    }

    @NotNull
    @Override
    public String getDebuggerDisplayName() {
      return myInfo.getDebuggerName();
    }

    @Override
    public void attachDebugSession(@NotNull Project project, @NotNull ProcessInfo processInfo) {
      attach(myInfo, myProject);
    }
  }

  private static final Key<Map<String, LocalAttachInfo>> ADDRESS_MAP_KEY = Key.create("ADDRESS_MAP");

  private static final XAttachPresentationGroup<ProcessInfo> ourAttachGroup = new JavaDebuggerAttachGroup(
    JavaDebuggerBundle.message("debugger.attach.group.name.java"), -20);

  static class JavaDebuggerAttachGroup implements XAttachProcessPresentationGroup {
    private final @Nls String myName;
    private final int myOrder;

    JavaDebuggerAttachGroup(@Nls String name, int order) {
      myName = name;
      myOrder = order;
    }

    @Override
    public int getOrder() {
      return myOrder;
    }

    @NotNull
    @Override
    public String getGroupName() {
      return myName;
    }

    @Nls
    @Override
    public @NotNull String getItemDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      LocalAttachInfo attachInfo = getAttachInfo(project, info.getPid(), info.getCommandLine(), dataHolder.getUserData(ADDRESS_MAP_KEY));
      assert attachInfo != null;
      String res;
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

    @Override
    public @NotNull Icon getItemIcon(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      return EmptyIcon.ICON_16;
    }
  }

  @Override
  public @NotNull XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return ourAttachGroup;
  }

  @Override
  public @NotNull List<? extends XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                                        @NotNull XAttachHost attachHost,
                                                                        @NotNull ProcessInfo processInfo,
                                                                        @NotNull UserDataHolder contextHolder) {
    Map<String, LocalAttachInfo> addressMap = contextHolder.getUserData(ADDRESS_MAP_KEY);
    if (addressMap == null) {
      addressMap = new HashMap<>();
      contextHolder.putUserData(ADDRESS_MAP_KEY, addressMap);
      final Map<String, LocalAttachInfo> map = addressMap;
      Set<String> attachedPids = JavaDebuggerAttachUtil.getAttachedPids(project);
      VirtualMachine.list().forEach(desc -> {
        String pid = desc.id();
        // no need to validate the process, it is already validated inside VirtualMachine.list()
        LocalAttachInfo address = getProcessAttachInfo(pid, attachedPids, false);
        if (address != null) {
          map.put(pid, address);
        }
      });
    }

    LocalAttachInfo info = getAttachInfo(project, processInfo.getPid(), processInfo.getCommandLine(), addressMap);
    if (info != null && isDebuggerAttach(info)) {
      return Collections.singletonList(new JavaLocalAttachDebugger(project, info));
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isAttachHostApplicable(@NotNull XAttachHost attachHost) {
    return attachHost instanceof LocalAttachHost;
  }

  boolean isDebuggerAttach(LocalAttachInfo info) {
    return info instanceof DebuggerLocalAttachInfo;
  }

  @Nullable
  private static LocalAttachInfo getAttachInfo(@Nullable Project project,
                                               int pid,
                                               String commandLine,
                                               @Nullable Map<String, LocalAttachInfo> addressMap) {
    LocalAttachInfo res;
    String pidString = String.valueOf(pid);
    if (addressMap != null) {
      res = addressMap.get(pidString);
      if (res != null) {
        return res;
      }
    }

    res = getProcessAttachInfo(ParametersListUtil.parse(commandLine), pidString);
    if (res == null && addressMap == null) {
      res =
        getProcessAttachInfo(pidString, project != null ? JavaDebuggerAttachUtil.getAttachedPids(project) : Collections.emptySet(), true);
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
              address = StringUtil.trimStart(address, "*:"); // handle java 9 format: *:5005
              return new DebuggerLocalAttachInfo(socket, address, null, pid, null, false);
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

  private static LocalAttachInfo getProcessAttachInfo(String pid, @NotNull Set<String> attachedPids, boolean validate) {
    if (!attachedPids.contains(pid)) {
      Future<LocalAttachInfo> future =
        ApplicationManager.getApplication().executeOnPooledThread(() -> getProcessAttachInfoInt(pid, validate));
      try {
        // attaching ang getting info may hang in some cases
        return future.get(5, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        LOG.info("Timeout while getting attach info", e);
      }
      finally {
        future.cancel(true);
      }
    }
    return null;
  }

  @Nullable
  static LocalAttachInfo getProcessAttachInfo(@NotNull BaseProcessHandler processHandler) {
    try {
      return getAttachInfo(null, (int)processHandler.getProcess().pid(), processHandler.getCommandLineForLog(), null);
    }
    catch (UnsupportedOperationException e) {
      return null;
    }
  }

  @Nullable
  private static LocalAttachInfo getProcessAttachInfoInt(String pid, boolean validate) {
    VirtualMachine vm = null;
    try {
      vm = validate ? JavaDebuggerAttachUtil.attachVirtualMachine(pid) : VirtualMachine.attach(pid);
      Properties agentProperties = vm.getAgentProperties();
      String command = agentProperties.getProperty("sun.java.command");
      if (!StringUtil.isEmpty(command)) {
        command = StringUtil.replace(command, AppMainV2.class.getName(), "").trim();
        command = StringUtil.notNullize(StringUtil.substringBefore(command, " "), command);
      }
      String property = agentProperties.getProperty("sun.jdwp.listenerAddress");
      if (property != null && property.indexOf(':') != -1) {
        boolean autoAddress = false;
        String args = agentProperties.getProperty("sun.jvm.args");
        if (!StringUtil.isEmpty(args)) {
          for (String arg : args.split(" ")) {
            if (arg.startsWith("-agentlib:jdwp")) {
              autoAddress = !arg.contains("address=");
              break;
            }
          }
        }
        return new DebuggerLocalAttachInfo(!"dt_shmem".equals(StringUtil.substringBefore(property, ":")),
                                           StringUtil.substringAfter(property, ":"),
                                           command, pid, null, autoAddress);
      }

      //do not allow further for idea process
      // read only attach is disabled on macos because of IDEA-252760
      if (!pid.equals(OSProcessUtil.getApplicationPid()) && !SystemInfo.isMac) {
        Properties systemProperties = vm.getSystemProperties();

        // prefer sa-jdwp attach if available
        // sa pid attach if sa-jdi.jar is available
        LocalAttachInfo info = SAJDWPLocalAttachInfo.create(systemProperties, command, pid);
        if (info != null) {
          return info;
        }

        // sa pid attach if sa-jdi.jar is available
        info = SAPIDLocalAttachInfo.create(systemProperties, command, pid);
        if (info != null) {
          return info;
        }
      }
    }
    catch (InternalError e) {
      LOG.warn(e);
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
    private final boolean myUseSocket;
    private final String myAddress;
    private final String mySessionName;
    private final boolean myAutoAddress;

    DebuggerLocalAttachInfo(boolean socket,
                            @NotNull String address,
                            String aClass,
                            String pid,
                            @Nullable String sessionName,
                            boolean autoAddress) {
      super(aClass, pid);
      myUseSocket = socket;
      myAddress = address;
      mySessionName = sessionName;
      myAutoAddress = autoAddress;
    }

    @Override
    RemoteConnection createConnection() {
      if (myAutoAddress) {
        return new PidRemoteConnection(myPid);
      }
      else {
        String host = DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK;
        String port = myAddress;
        int pos = port.indexOf(":");
        if (pos != -1) {
          host = port.substring(0, pos);
          port = port.substring(pos + 1);
        }
        if (!StringUtil.isEmpty(myPid)) {
          return new PidRemoteConnection(myPid, myUseSocket, host, port, false);
        }
        else {
          return new RemoteConnection(myUseSocket, host, port, false);
        }
      }
    }

    @Override
    String getSessionName() {
      if (mySessionName != null) {
        return mySessionName;
      }

      if (myAutoAddress) {
        return super.getSessionName();
      }
      else {
        return myAddress.contains(":") ? myAddress : "localhost:" + myAddress;
      }
    }

    @Override
    String getDebuggerName() {
      return "Java Debugger";
    }

    @Override
    String getProcessDisplayText(String text) {
      return text + " (" + myAddress + ")";
    }
  }

  static class SAPIDLocalAttachInfo extends LocalAttachInfo {
    final String mySAJarPath;

    SAPIDLocalAttachInfo(String aClass, String pid, String SAJarPath) {
      super(aClass, pid);
      mySAJarPath = SAJarPath;
    }

    @Nullable
    static SAPIDLocalAttachInfo create(Properties systemProperties, String aClass, String pid) throws IOException {
      File saJdiJar = new File(systemProperties.getProperty("java.home"), "../lib/sa-jdi.jar"); // java 8 only for now
      if (saJdiJar.exists()) {
        return new SAPIDLocalAttachInfo(aClass, pid, saJdiJar.getCanonicalPath());
      }
      return null;
    }

    @Override
    RemoteConnection createConnection() {
      return new SAPidRemoteConnection(myPid, mySAJarPath);
    }

    @Override
    String getDebuggerName() {
      return "Read Only Java Debugger";
    }
  }

  static class SAJDWPLocalAttachInfo extends LocalAttachInfo {
    private final List<String> myCommands;

    SAJDWPLocalAttachInfo(String aClass, String pid, List<String> commands) {
      super(aClass, pid);
      myCommands = commands;
    }

    @Nullable
    static LocalAttachInfo create(Properties systemProperties, String aClass, String pid) {
      try {
        List<String> commands =
          SaJdwp.getServerProcessCommand(systemProperties, pid, "0", false, PathUtil.getJarPathForClass(SaJdwp.class));
        return new SAJDWPLocalAttachInfo(aClass, pid, commands);
      }
      catch (Exception ignored) {
      }
      return null;
    }

    @Override
    RemoteConnection createConnection() {
      return new SAJDWPRemoteConnection(myPid, myCommands);
    }

    @Override
    String getDebuggerName() {
      return "Read Only Java Debugger";
    }
  }

  static abstract class LocalAttachInfo {
    final String myClass;
    final String myPid;

    LocalAttachInfo(String aClass, String pid) {
      myClass = aClass;
      myPid = pid;
    }

    abstract RemoteConnection createConnection();

    String getSessionName() {
      return "pid " + myPid;
    }

    @NlsSafe
    abstract String getDebuggerName();

    @NlsSafe
    String getProcessDisplayText(String text) {
      return text;
    }
  }

  private static final class ProcessAttachDebugExecutor extends DefaultDebugExecutor {
    static final ProcessAttachDebugExecutor INSTANCE = new ProcessAttachDebugExecutor();

    private ProcessAttachDebugExecutor() {
    }

    @NotNull
    @Override
    public String getId() {
      return "ProcessAttachDebug";
    }
  }

  public static final class ProcessAttachDebuggerRunner extends GenericDebuggerRunner {
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

  private static class ProcessAttachRunConfiguration extends SyntheticRunConfigurationBase<Element> {
    private LocalAttachInfo myAttachInfo;

    protected ProcessAttachRunConfiguration(@NotNull Project project) {
      super(project, ProcessAttachRunConfigurationType.FACTORY, "ProcessAttachRunConfiguration");
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
      return new RemoteStateState(getProject(), myAttachInfo.createConnection());
    }
  }

  private static final class ProcessAttachRunConfigurationType implements ConfigurationType {
    static final ProcessAttachRunConfigurationType INSTANCE = new ProcessAttachRunConfigurationType();
    static final ConfigurationFactory FACTORY = new ConfigurationFactory(INSTANCE) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new ProcessAttachRunConfiguration(project);
      }

      @Override
      public @NotNull String getId() {
        return INSTANCE.getId();
      }
    };

    @NotNull
    @Nls
    @Override
    public String getDisplayName() {
      return JavaDebuggerBundle.message("process.attach.run.configuration.type.name");
    }

    @Nls
    @Override
    public String getConfigurationTypeDescription() {
      return getDisplayName();
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

    @Override
    public String getHelpTopic() {
      return "reference.dialogs.rundebug.ProcessAttachRunConfigurationType";
    }
  }

  public static void attach(String transport, String address, String sessionName, Project project) {
    attach(new DebuggerLocalAttachInfo(!"dt_shmem".equals(transport), address, null, null, sessionName, false), project);
  }

  static void attach(JavaAttachDebuggerProvider.LocalAttachInfo info, Project project) {
    RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project)
      .createConfiguration(info.getSessionName(), JavaAttachDebuggerProvider.ProcessAttachRunConfigurationType.FACTORY);
    ((JavaAttachDebuggerProvider.ProcessAttachRunConfiguration)runSettings.getConfiguration()).myAttachInfo = info;
    ProgramRunnerUtil.executeConfiguration(runSettings, JavaAttachDebuggerProvider.ProcessAttachDebugExecutor.INSTANCE);
  }
}

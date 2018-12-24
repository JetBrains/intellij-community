// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMainV2;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.xdebugger.attach.XDefaultLocalAttachGroup;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import com.jetbrains.sa.SaJdwp;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author egor
 */
public class JavaAttachDebuggerProvider implements XLocalAttachDebuggerProvider {
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

  private static final XLocalAttachGroup ourAttachGroup = new JavaDebuggerAttachGroup("Java", -20);

  static class JavaDebuggerAttachGroup extends XDefaultLocalAttachGroup {
    private final String myName;
    private final int myOrder;

    JavaDebuggerAttachGroup(String name, int order) {
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

    @NotNull
    @Override
    public String getProcessDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
      LocalAttachInfo attachInfo = getAttachInfo(project, info, dataHolder.getUserData(ADDRESS_MAP_KEY));
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
      Set<String> attachedPids = JavaDebuggerAttachUtil.getAttachedPids(project);
      VirtualMachine.list().forEach(desc -> {
        String pid = desc.id();
        LocalAttachInfo address = getProcessAttachInfo(pid, attachedPids);
        if (address != null) {
          map.put(pid, address);
        }
      });
    }

    LocalAttachInfo info = getAttachInfo(project, processInfo, addressMap);
    if (info != null && isDebuggerAttach(info)) {
      return Collections.singletonList(new JavaLocalAttachDebugger(project, info));
    }
    return Collections.emptyList();
  }

  boolean isDebuggerAttach(LocalAttachInfo info) {
    return info instanceof DebuggerLocalAttachInfo;
  }

  @Nullable
  private static LocalAttachInfo getAttachInfo(Project project,
                                               ProcessInfo processInfo,
                                               @Nullable Map<String, LocalAttachInfo> addressMap) {
    LocalAttachInfo res;
    String pidString = String.valueOf(processInfo.getPid());
    if (addressMap != null) {
      res = addressMap.get(pidString);
    }
    else {
      res = getProcessAttachInfo(pidString, project);
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
              return new DebuggerLocalAttachInfo(socket, address, null, pid, false);
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

  private static LocalAttachInfo getProcessAttachInfo(String pid, @NotNull Set<String> attachedPids) {
    if (!attachedPids.contains(pid)) {
      Future<LocalAttachInfo> future = ApplicationManager.getApplication().executeOnPooledThread(() -> getProcessAttachInfoInt(pid));
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
  private static LocalAttachInfo getProcessAttachInfo(@NotNull String pid, @NotNull Project project) {
    return getProcessAttachInfo(pid, JavaDebuggerAttachUtil.getAttachedPids(project));
  }

  @Nullable
  static LocalAttachInfo getProcessAttachInfo(@NotNull String pid) {
    return getProcessAttachInfo(pid, Collections.emptySet());
  }

  @Nullable
  private static LocalAttachInfo getProcessAttachInfoInt(String pid) {
    VirtualMachine vm = null;
    try {
      vm = JavaDebuggerAttachUtil.attachVirtualMachine(pid);
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
                                           command, pid, autoAddress);
      }

      //do not allow further for idea process
      if (!pid.equals(OSProcessUtil.getApplicationPid())) {
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
    private final boolean myAutoAddress;

    DebuggerLocalAttachInfo(boolean socket, @NotNull String address, String aClass, String pid, boolean autoAddress) {
      super(aClass, pid);
      myUseSocket = socket;
      myAddress = address;
      myAutoAddress = autoAddress;
    }

    @Override
    RemoteConnection createConnection() {
      return myAutoAddress
             ? new PidRemoteConnection(myPid)
             : new PidRemoteConnection(myPid, myUseSocket, DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK, myAddress, false);
    }

    @Override
    String getSessionName() {
      return myAutoAddress ? super.getSessionName() : "localhost:" + myAddress;
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
        List<String> commands = SaJdwp.getServerProcessCommand(systemProperties, pid, "0", false, PathUtil.getJarPathForClass(SaJdwp.class));
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

    abstract String getDebuggerName();

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

  private static final class ProcessAttachRunConfigurationType implements ConfigurationType {
    static final ProcessAttachRunConfigurationType INSTANCE = new ProcessAttachRunConfigurationType();
    static final ConfigurationFactory FACTORY = new ConfigurationFactory(INSTANCE) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new ProcessAttachRunConfiguration(project);
      }
    };

    @NotNull
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

    @Override
    public String getHelpTopic() {
      return "reference.dialogs.rundebug.ProcessAttachRunConfigurationType";
    }
  }

  static void attach(JavaAttachDebuggerProvider.LocalAttachInfo info, Project project) {
    RunnerAndConfigurationSettings runSettings =
      RunManager
        .getInstance(project).createConfiguration(info.getSessionName(), JavaAttachDebuggerProvider.ProcessAttachRunConfigurationType.FACTORY);
    ((JavaAttachDebuggerProvider.ProcessAttachRunConfiguration)runSettings.getConfiguration()).myAttachInfo = info;
    ProgramRunnerUtil.executeConfiguration(runSettings, JavaAttachDebuggerProvider.ProcessAttachDebugExecutor.INSTANCE);
  }
}

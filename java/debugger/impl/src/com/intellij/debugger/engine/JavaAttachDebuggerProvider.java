// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.application.AppMainV2;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.xdebugger.attach.XDefaultLocalAttachGroup;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author egor
 */
public class JavaAttachDebuggerProvider implements XLocalAttachDebuggerProvider {
  private static final List<XLocalAttachDebugger> ourAttachDebuggers = Collections.singletonList(new XLocalAttachDebugger() {
    @NotNull
    @Override
    public String getDebuggerDisplayName() {
      return "Java Debugger";
    }

    @Override
    public void attachDebugSession(@NotNull Project project, @NotNull ProcessInfo processInfo) {
      LocalAttachInfo info = getAttachInfo(processInfo, null);
      assert info != null;

      // TODO: first need to remove circular dependency with intellij.java.execution.impl
      //RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project)
      //  .createRunConfiguration(StringUtil.notNullize(address.first) + ":" + address.second,
      //                          RemoteConfigurationType.getInstance().getFactory());
      //
      //RemoteConfiguration configuration = (RemoteConfiguration)runSettings.getConfiguration();
      //configuration.HOST = address.first;
      //configuration.PORT = String.valueOf(address.second);
      //configuration.USE_SOCKET_TRANSPORT = true;
      //configuration.SERVER_MODE = false;

      RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project).createRunConfiguration(
        "localhost:" + info.myAddress,
        Objects.requireNonNull(ConfigurationTypeUtil.findConfigurationType("Remote")).getConfigurationFactories()[0]);

      RunConfiguration remoteConfiguration = runSettings.getConfiguration();
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, String.class, "HOST",
                              DebuggerManagerImpl.LOCALHOST_ADDRESS_FALLBACK);
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, String.class,
                              info.myUseSocket ? "PORT" : "SHMEM_ADDRESS", info.myAddress);
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, boolean.class, "USE_SOCKET_TRANSPORT",
                              info.myUseSocket);
      ReflectionUtil.setField(remoteConfiguration.getClass(), remoteConfiguration, boolean.class, "SERVER_MODE", false);

      ProgramRunnerUtil.executeConfiguration(runSettings, DefaultDebugExecutor.getDebugExecutorInstance());
    }
  });

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
      StringBuilder res = new StringBuilder();
      String executable = info.getExecutableDisplayName();
      if ("java".equals(executable)) {
        if (!StringUtil.isEmpty(attachInfo.myClass)) {
          res.append(attachInfo.myClass);
        }
        else {
          res.append(StringUtil.notNullize(ArrayUtil.getLastElement(info.getCommandLine().split(" ")))); // should be class name
        }
      }
      else {
        res.append(executable);
      }
      return res.append(" (").append(attachInfo.myAddress).append(')').toString();
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

    return getAttachInfo(processInfo, addressMap) != null ? ourAttachDebuggers : Collections.emptyList();
  }

  @Nullable
  private static LocalAttachInfo getAttachInfo(ProcessInfo processInfo, @Nullable Map<String, LocalAttachInfo> addressMap) {
    LocalAttachInfo res;
    if (addressMap != null) {
      res = addressMap.get(String.valueOf(processInfo.getPid()));
    }
    else {
      res = getProcessAttachInfo(String.valueOf(processInfo.getPid()));
    }
    if (res == null) {
      Pair<String, Integer> address = DebugAttachDetector.getAttachAddress(ParametersListUtil.parse(processInfo.getCommandLine()));
      if (address != null) {
        res = new LocalAttachInfo(true, String.valueOf(address.getSecond()), null);
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
      String property = agentProperties.getProperty("sun.jdwp.listenerAddress");
      if (property != null && property.indexOf(':') != -1) {
        String command = agentProperties.getProperty("sun.java.command");
        if (!StringUtil.isEmpty(command)) {
          command = StringUtil.replace(command, AppMainV2.class.getName(), "").trim();
          command = StringUtil.substringBefore(command, " ");
        }
        return new LocalAttachInfo(!"dt_shmem".equals(StringUtil.substringBefore(property, ":")),
                                   StringUtil.substringAfter(property, ":"),
                                   command);
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

  private static class LocalAttachInfo {
    final boolean myUseSocket;
    final String myAddress;
    final String myClass;

    private LocalAttachInfo(boolean socket, @NotNull String address, String aClass) {
      myUseSocket = socket;
      myAddress = address;
      myClass = aClass;
    }
  }
}

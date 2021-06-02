// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.TaskExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.execution.target.HostPort;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.wsl.WSLCommandLineOptions;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.target.proxy.WslProxy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LineSeparator;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class WslTargetLocalPortBindingsSession {

  private static final Logger LOG = Logger.getInstance(WslTargetLocalPortBindingsSession.class);

  private final WSLDistribution myDistribution;
  private final List<BindingSession> mySessions;
  private final boolean myWsl1;

  public WslTargetLocalPortBindingsSession(@NotNull WSLDistribution distribution,
                                           @NotNull Collection<TargetEnvironment.LocalPortBinding> localPortBindings) {
    myDistribution = distribution;
    mySessions = ContainerUtil.map(localPortBindings, binding -> new BindingSession(binding));
    myWsl1 = WSLUtil.isWsl1(myDistribution) == ThreeState.YES;
  }

  public @NotNull CompletableFuture<HostPort> getTargetHostPortFuture(@NotNull TargetEnvironment.LocalPortBinding localPortBinding) {
    if (myWsl1) {
      // Ports bound on localhost in Windows can be accessed by linux apps running in WSL1, but not in WSL2:
      //   https://docs.microsoft.com/en-US/windows/wsl/compare-versions#accessing-network-applications
      return CompletableFuture.completedFuture(new HostPort("localhost", localPortBinding.getLocal()));
    }
    BindingSession session = Objects.requireNonNull(ContainerUtil.find(mySessions, s -> s.myPortBinding.equals(localPortBinding)));
    return session.getTargetHostPortFuture();
  }

  public void start() {
    if (myWsl1) {
      return;
    }
    for (BindingSession session : mySessions) {
      HostPort hostAddr = session.startProxyOnHostIp();
      if (hostAddr == null) {
        // Failed to start proxy on Windows. However, there is hope that local port was bound on WSL Host IP too, not only on 127.0.0.1.
        hostAddr = new HostPort(myDistribution.getHostIp(), session.myPortBinding.getLocal());
      }
      session.startProxyOnWsl(hostAddr);
    }
  }

  public void stopWhenProcessTerminated(@NotNull Process process) {
    if (mySessions.isEmpty()) return;
    TaskExecutor executor = new TaskExecutor() {
      @Override
      public @NotNull Future<?> executeTask(@NotNull Runnable task) {
        return ApplicationManager.getApplication().executeOnPooledThread(task);
      }
    };
    new ProcessWaitFor(process, executor, "Cleanup WSL local port binding").setTerminationCallback(exitCode -> {
      for (BindingSession session : mySessions) {
        session.stop();
      }
    });
  }

  private class BindingSession {
    private final TargetEnvironment.LocalPortBinding myPortBinding;
    private SimpleProxy myHostProxy;
    private File myWslProxyPyFile;
    private KillableProcessHandler myWslProxyProcess;
    private final CompletableFuture<HostPort> myTargetHostPortFuture = new CompletableFuture<>();

    private BindingSession(TargetEnvironment.LocalPortBinding portBinding) {
      myPortBinding = portBinding;
    }

    public @Nullable HostPort startProxyOnHostIp() {
      String hostIp = myDistribution.getHostIp();
      try {
        myHostProxy = new SimpleProxy(hostIp, new HostPort("127.0.0.1", myPortBinding.getLocal()));
        return new HostPort(hostIp, myHostProxy.getListenPort());
      }
      catch (IOException e) {
        LOG.info("Cannot start local proxy", e);
      }
      return null;
    }

    public void startProxyOnWsl(@NotNull HostPort remoteAddr) {
      String host = remoteAddr.getHost();
      int port = remoteAddr.getPort();
      try {
        myWslProxyPyFile = WslProxy.createPyFile();
      }
      catch (IOException e) {
        LOG.info("Cannot create wsl proxy file", e);
        return;
      }

      String scriptPath = myDistribution.getWslPath(myWslProxyPyFile.getAbsolutePath());
      if (scriptPath == null) {
        return;
      }
      try {
        GeneralCommandLine commandLine = new GeneralCommandLine("/usr/bin/python3", scriptPath,
                                                                "127.0.0.1", host, String.valueOf(port));
        myDistribution.patchCommandLine(commandLine, null, new WSLCommandLineOptions().setExecuteCommandInShell(false));
        myWslProxyProcess = new KillableProcessHandler(commandLine);
        StringBuilder output = new StringBuilder();
        myWslProxyProcess.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (ProcessOutputType.isStdout(outputType)) {
              output.append(event.getText());
              int port = parsePort(output.toString());
              if (port >= 0) {
                myTargetHostPortFuture.complete(new HostPort(myDistribution.getHostIp(), port));
              }
            }
            LOG.info("wsl2_proxy.py: " + StringUtil.trimEnd(event.getText(), LineSeparator.LF.getSeparatorString()));
          }

          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            LOG.info("wsl2_proxy.py: terminated with " + event.getExitCode());
          }
        });
        myWslProxyProcess.startNotify();
      }
      catch (ExecutionException e) {
        LOG.info("Cannot run proxy in WSL " + myDistribution.getMsId(), e);
      }
    }

    public void stop() {
      if (myWslProxyProcess != null) {
        ScriptRunnerUtil.terminateProcessHandler(myWslProxyProcess, 1000, null);
      }
      if (myWslProxyPyFile != null) {
        FileUtil.delete(myWslProxyPyFile);
      }
      if (myHostProxy != null) {
        myHostProxy.stop();
      }
    }

    public @NotNull CompletableFuture<HostPort> getTargetHostPortFuture() {
      return myTargetHostPortFuture;
    }
  }

  private static final String PREFIX = "IntelliJ WSL proxy is listening on port ";

  private static int parsePort(@NotNull String text) {
    // parsing "IntelliJ WSL proxy is listening on port {0}, ready for connections" from wsl2_proxy.py
    int startInd = text.indexOf(PREFIX);
    if (startInd >= 0) {
      int nextTextInd = text.indexOf(", ready for connections", startInd);
      if (nextTextInd >= 0) {
        String port = text.substring(startInd + PREFIX.length(), nextTextInd);
        return StringUtil.parseInt(port, -1);
      }
    }
    return -1;
  }
}

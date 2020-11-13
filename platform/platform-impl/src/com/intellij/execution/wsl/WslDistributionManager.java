// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service(Service.Level.APP)
public final class WslDistributionManager {

  private static final Logger LOG = Logger.getInstance(WslDistributionManager.class);
  // Distributions created by tools, e.g. Docker. Not suitable for running users apps.
  private static final Set<String> INTERNAL_DISTRIBUTIONS = Set.of("docker-desktop-data");

  public static @NotNull WslDistributionManager getInstance() {
    return ApplicationManager.getApplication().getService(WslDistributionManager.class);
  }

  public @NotNull List<WSLDistribution> getInstalledDistributions() {
    checkEdtAndReadAction();
    List<WSLDistribution> parsedFromWslList;
    try {
      long startNano = System.nanoTime();
      parsedFromWslList = parseFromWslList();
      LOG.info("Installed WSL distributions parsed in " + TimeoutUtil.getDurationMillis(startNano) + "ms");
    }
    catch (IOException e) {
      LOG.info("Cannot parse WSL distributions", e);
      parsedFromWslList = Collections.emptyList();
    }
    List<WSLDistribution> withExecutable = WSLUtil.getAvailableDistributions();
    List<WSLDistribution> result = new ArrayList<>(withExecutable);
    for (WSLDistribution parsedDistribution : parsedFromWslList) {
      if (withExecutable.stream().noneMatch((d) -> d.getMsId().equals(parsedDistribution.getMsId()))) {
        result.add(parsedDistribution);
      }
    }
    return result;
  }

  private static @NotNull List<WSLDistribution> parseFromWslList() throws IOException {
    GeneralCommandLine commandLine = createCommandLine();
    commandLine.setCharset(StandardCharsets.UTF_16LE);
    ProcessOutput output;
    try {
      output = ExecUtil.execAndGetOutput(commandLine, 10000);
    }
    catch (ExecutionException e) {
      throw new IOException("Failed to run " + commandLine.getCommandLineString(), e);
    }
    if (output.isTimeout() || output.getExitCode() != 0 || !output.getStderr().isEmpty()) {
      String details = StringUtil.join(ContainerUtil.newArrayList(
        "timeout: " + output.isTimeout(),
        "exitCode: " + output.getExitCode(),
        "stdout: " + output.getStdout(),
        "stderr: " + output.getStderr()
      ), ", ");
      throw new IOException("Failed to run " + commandLine.getCommandLineString() + ": " + details);
    }
    List<@NlsSafe String> distributions = ContainerUtil.filter(output.getStdoutLines(), distribution -> {
      return !INTERNAL_DISTRIBUTIONS.contains(distribution);
    });
    return ContainerUtil.map(distributions, WSLDistribution::new);
  }

  private static @NotNull GeneralCommandLine createCommandLine() throws IOException {
    File wslExe = PathEnvironmentVariableUtil.findInPath("wsl.exe");
    if (wslExe == null) {
      throw new IOException("No wsl.exe found in %PATH%");
    }
    return new GeneralCommandLine(wslExe.getAbsolutePath(), "--list", "--quiet");
  }

  private static void checkEdtAndReadAction() {
    Application application = ApplicationManager.getApplication();
    if (application == null || !application.isInternal() || application.isHeadlessEnvironment()) {
      return;
    }
    if (application.isReadAccessAllowed()) {
      LOG.error("Please call WslDistributionManager.getInstalledDistributions on a background thread and " +
                "not under read action as it runs a potentially long operation.");
    }
  }
}

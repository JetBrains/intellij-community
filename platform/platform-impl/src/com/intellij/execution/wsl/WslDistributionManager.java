// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WslDistributionManager {

  private static final Logger LOG = Logger.getInstance(WslDistributionManager.class);
  private static final WslDistributionManager INSTANCE = new WslDistributionManager();

  public static @NotNull WslDistributionManager getInstance() {
    return INSTANCE;
  }

  public @NotNull List<WSLDistribution> getInstalledDistributions() {
    List<WSLDistribution> parsedFromWslList;
    try {
      long startNano = System.nanoTime();
      parsedFromWslList = parseFromWslList();
      LOG.info("Installed WSL distributions parsed in " + TimeoutUtil.getDurationMillis(startNano) + "ms");
    }
    catch (ExecutionException e) {
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

  private static @NotNull List<WSLDistribution> parseFromWslList() throws ExecutionException {
    GeneralCommandLine commandLine = createCommandLine();
    commandLine.setCharset(StandardCharsets.UTF_16LE);
    ProcessOutput output = ExecUtil.execAndGetOutput(commandLine, 5000);
    if (output.isTimeout()) {
      throw new ExecutionException(commandLine.getCommandLineString() + " is timed out"); //NON-NLS
    }
    if (output.getExitCode() != 0) {
      throw new ExecutionException(commandLine.getCommandLineString() + " has terminated with exit code " + output.getExitCode()); //NON-NLS
    }
    if (!output.getStderr().isEmpty()) {
      throw new ExecutionException(commandLine.getCommandLineString() + " failed with " + output.getStderr()); //NON-NLS
    }
    List<@NlsSafe String> lines = output.getStdoutLines();
    return ContainerUtil.map(lines, WSLDistribution::new);
  }

  private static @NotNull GeneralCommandLine createCommandLine() throws ExecutionException {
    File wslExe = PathEnvironmentVariableUtil.findInPath("wsl.exe");
    if (wslExe == null) {
      throw new ExecutionException("No wsl.exe found in %PATH%"); //NON-NLS
    }
    return new GeneralCommandLine(wslExe.getAbsolutePath(), "--list", "--quiet");
  }

}

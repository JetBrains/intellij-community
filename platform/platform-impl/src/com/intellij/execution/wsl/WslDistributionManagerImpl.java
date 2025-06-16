// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class WslDistributionManagerImpl extends WslDistributionManager {

  // Distributions created by tools, e.g. Docker. Not suitable for running user apps.
  private static final Set<String> INTERNAL_DISTRIBUTIONS = Set.of("docker-desktop-data", "docker-desktop");

  @Override
  protected @NotNull List<String> loadInstalledDistributionMsIds() {
    checkEdtAndReadAction();
    if (!new WSLCommandLineOptions().isLaunchWithWslExe()) {
      return Collections.emptyList();
    }
    try {
      long startNano = System.nanoTime();
      Pair<GeneralCommandLine, List<String>> result = doFetchDistributionsFromWslCli();
      if (result == null) return Collections.emptyList();

      LOG.info("Fetched WSL distributions: " + result.second +
               " (\"" + result.first.getCommandLineString() + "\" done in " + TimeoutUtil.getDurationMillis(startNano) + " ms)");
      return result.second;
    }
    catch (IOException e) {
      LOG.info("Cannot parse WSL distributions", e);
      return Collections.emptyList();
    }
  }

  @Override
  public @NotNull List<WslDistributionAndVersion> loadInstalledDistributionsWithVersions() throws IOException, IllegalStateException {
    checkEdtAndReadAction();
    Path wslExe = WSLDistribution.findWslExe();
    if (wslExe == null) {
      LOG.info("Cannot load WSL distributions with versions: wsl.exe is not found in %PATH%");
      return List.of();
    }

    GeneralCommandLine commandLine = new GeneralCommandLine(wslExe.toString(), "--list", "--verbose").
      withCharset(StandardCharsets.UTF_16LE);

    long startNano = System.nanoTime();
    ProcessOutput output;
    try {
      output = ExecUtil.execAndGetOutput(commandLine, WSLDistribution.DEFAULT_TIMEOUT);
    }
    catch (ExecutionException e) {
      throw new IOException("Failed to run " + commandLine.getCommandLineString(), e);
    }
    // Windows Subsystem for Linux has no installed distributions
    if (output.getExitCode() != 0 && output.getStdout().trim().endsWith("https://aka.ms/wslstore")) {
      LOG.info("Windows Subsystem for Linux has no installed distributions");
      return Collections.emptyList();
    }
    if (isWslDisabled(output)) {
      LOG.info("WSL is disabled in the system");
      return Collections.emptyList();
    }
    if (output.isTimeout() || output.getExitCode() != 0 || !output.getStderr().isEmpty()) {
      throw new IOException("Failed to run " + commandLine.getCommandLineString() + ": " + output +
                            ", done in " + TimeoutUtil.getDurationMillis(startNano) + " ms");
    }
    List<WslDistributionAndVersion> versions = parseWslVerboseListOutput(output.getStdoutLines());
    LOG.info("Fetched WSL distributions: " + versions +
             " (\"" + commandLine.getCommandLineString() + "\" done in " + TimeoutUtil.getDurationMillis(startNano) + " ms)");
    return versions;
  }

  private static boolean isWslDisabled(@NotNull ProcessOutput output) {
    if (output.getExitCode() == 0) {
      return false;
    }
    String stdout = output.getStdout();
    return stdout.contains("--install") && stdout.contains("--list") && stdout.contains("--help");
  }

  /**
   * @throws IllegalStateException in case of parsing error
   */
  @VisibleForTesting
  public static @NotNull List<WslDistributionAndVersion> parseWslVerboseListOutput(@NotNull List<String> stdoutLines)
    throws IllegalStateException {
    if (stdoutLines.isEmpty()) {
      throw new IllegalStateException("[wsl -l -v] parsing error: stdout is empty");
    }

    // skip the first line: "NAME  STATE  VERSION"
    stdoutLines = ContainerUtil.subList(stdoutLines, 1);
    final List<WslDistributionAndVersion> result = new ArrayList<>(stdoutLines.size());

    for (String l : stdoutLines) {
      List<String> words = StringUtil.split(l, " ");
      if ("*".equals(ContainerUtil.getFirstItem(words))) {
        words = ContainerUtil.subList(words, 1);
      }

      final String distributionName = ContainerUtil.getFirstItem(words);
      if (StringUtil.isEmpty(distributionName)) {
        throw new IllegalStateException("[wsl -l -v] parsing error: malformed distribution name" +
                                        "\nline: " + l +
                                        "\nstdout: " + StringUtil.join(stdoutLines, "\n"));
      }
      final int version = StringUtil.parseInt(ContainerUtil.getLastItem(words), -1);
      if (version == -1) {
        throw new IllegalStateException("[wsl -l -v] parsing error: malformed version" +
                                        "\nline: " + l +
                                        "\nstdout: " + StringUtil.join(stdoutLines, "\n"));
      }
      if (!INTERNAL_DISTRIBUTIONS.contains(distributionName)) {
        result.add(new WslDistributionAndVersion(distributionName, version));
      }
    }
    return result;
  }

  private static @Nullable Pair<GeneralCommandLine, List<String>> doFetchDistributionsFromWslCli() throws IOException {
    Path wslExe = WSLDistribution.findWslExe();
    if (wslExe == null) {
      LOG.info("Cannot parse WSL distributions: wsl.exe is not found in %PATH%");
      return null;
    }

    GeneralCommandLine commandLine = new GeneralCommandLine(wslExe.toString(), "--list", "--quiet").withCharset(StandardCharsets.UTF_16LE);

    ProcessOutput output;
    try {
      output = ExecUtil.execAndGetOutput(commandLine, WSLDistribution.DEFAULT_TIMEOUT);
    }
    catch (ExecutionException e) {
      throw new IOException("Failed to run " + commandLine.getCommandLineString(), e);
    }
    if (output.isTimeout() || output.getExitCode() != 0 || !output.getStderr().isEmpty()) {
      throw new IOException("Failed to run " + commandLine.getCommandLineString() + ": " + output);
    }
    List<@NlsSafe String> msIds = ContainerUtil.filter(output.getStdoutLines(true), distribution -> {
      return !INTERNAL_DISTRIBUTIONS.contains(distribution);
    });
    return Pair.create(commandLine, msIds);
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

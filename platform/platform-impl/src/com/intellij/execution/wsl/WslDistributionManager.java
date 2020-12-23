// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service(Service.Level.APP)
public final class WslDistributionManager implements Disposable {

  private static final Logger LOG = Logger.getInstance(WslDistributionManager.class);
  // Distributions created by tools, e.g. Docker. Not suitable for running users apps.
  private static final Set<String> INTERNAL_DISTRIBUTIONS = Set.of("docker-desktop-data");
  private static final Object LOCK = new Object();

  public static @NotNull WslDistributionManager getInstance() {
    return ApplicationManager.getApplication().getService(WslDistributionManager.class);
  }

  private volatile CachedDistributions myCachedDistributions;

  @Override
  public void dispose() {
  }

  public @NotNull List<WSLDistribution> getInstalledDistributions() {
    CachedDistributions cachedDistributions = myCachedDistributions;
    if (cachedDistributions != null && cachedDistributions.isUpToDate()) {
      return cachedDistributions.myInstalledDistributions;
    }
    myCachedDistributions = null;
    synchronized (LOCK) {
      cachedDistributions = myCachedDistributions;
      if (cachedDistributions == null) {
        cachedDistributions = new CachedDistributions(loadInstalledDistributions());
        myCachedDistributions = cachedDistributions;
      }
    }
    return cachedDistributions.myInstalledDistributions;
  }

  public @NotNull CompletableFuture<List<WSLDistribution>> getInstalledDistributionsFuture() {
    CachedDistributions cachedDistributions = myCachedDistributions;
    if (cachedDistributions != null && cachedDistributions.isUpToDate()) {
      return CompletableFuture.completedFuture(cachedDistributions.myInstalledDistributions);
    }
    return CompletableFuture.supplyAsync(this::getInstalledDistributions, AppExecutorUtil.getAppExecutorService());
  }

  public WSLDistribution getDistributionByMsId(@Nullable String name) {
    if (name == null) {
      return null;
    }
    for (WSLDistribution distribution : getInstalledDistributions()) {
      if (name.equalsIgnoreCase(distribution.getMsId())) {
        return distribution;
      }
    }
    return null;
  }

  @Nullable
  public Pair<String, @Nullable WSLDistribution> parseWslPath(@NotNull String path) {
    if (!WSLUtil.isSystemCompatible()) return null;
    path = FileUtil.toSystemDependentName(path);
    if (!path.startsWith(WSLDistribution.UNC_PREFIX)) return null;

    path = StringUtil.trimStart(path, WSLDistribution.UNC_PREFIX);
    int index = path.indexOf('\\');
    if (index == -1) return null;

    String distName = path.substring(0, index);
    String wslPath = FileUtil.toSystemIndependentName(path.substring(index));

    WSLDistribution distribution = getDistributionByMsId(distName);
    if (distribution == null) {
      LOG.debug(String.format("Unknown WSL distribution: %s, known distributions: %s", distName,
                              StringUtil.join(getInstalledDistributions(), WSLDistribution::getMsId, ", ")));
    }
    return Pair.create(wslPath, distribution);
  }

  @Nullable
  public WSLDistribution distributionFromPath(@NotNull String path) {
    Pair<String, @Nullable WSLDistribution> pair = parseWslPath(path);
    if (pair != null && pair.second != null) {
      return pair.second;
    }
    return null;
  }

  public static boolean isWslPath(@NotNull String path) {
    return FileUtil.toSystemDependentName(path).startsWith(WSLDistribution.UNC_PREFIX);
  }

  @NotNull
  private static List<WSLDistribution> loadInstalledDistributions() {
    checkEdtAndReadAction();
    List<WSLDistribution> wslCliDistributions = fetchDistributionsFromWslCli();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return wslCliDistributions;
    }
    List<WSLDistribution> oldDistributionsWithExecutable = WSLUtil.getAvailableDistributions();
    List<WSLDistribution> result = new ArrayList<>(oldDistributionsWithExecutable);
    // Prefer distributions with executables as it allows to switch to the old execution schema.
    for (WSLDistribution parsedDistribution : wslCliDistributions) {
      if (oldDistributionsWithExecutable.stream().noneMatch((d) -> d.getMsId().equals(parsedDistribution.getMsId()))) {
        result.add(parsedDistribution);
      }
    }
    return result;
  }

  private static @NotNull List<WSLDistribution> fetchDistributionsFromWslCli() {
    if (!new WSLCommandLineOptions().isLaunchWithWslExe()) {
      return Collections.emptyList();
    }
    try {
      long startNano = System.nanoTime();
      Pair<GeneralCommandLine, List<WSLDistribution>> result = doFetchDistributionsFromWslCli();
      LOG.info("Fetched WSL distributions: " + ContainerUtil.map(result.second, WSLDistribution::getMsId) +
               " (\"" + result.first.getCommandLineString() + "\" done in " + TimeoutUtil.getDurationMillis(startNano) + " ms)");
      return result.second;
    }
    catch (IOException e) {
      LOG.info("Cannot parse WSL distributions", e);
      return Collections.emptyList();
    }
  }

  private static @NotNull Pair<GeneralCommandLine, List<WSLDistribution>> doFetchDistributionsFromWslCli() throws IOException {
    GeneralCommandLine commandLine = createCommandLine();
    ProcessOutput output;
    try {
      output = ExecUtil.execAndGetOutput(commandLine, 10_000);
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
    return Pair.create(commandLine, ContainerUtil.map(distributions, WSLDistribution::new));
  }

  private static @NotNull GeneralCommandLine createCommandLine() throws IOException {
    Path wslExe = WSLDistribution.findWslExe();
    if (wslExe == null) {
      throw new IOException("No wsl.exe found in %PATH%");
    }
    return new GeneralCommandLine(wslExe.toString(), "--list", "--quiet").withCharset(StandardCharsets.UTF_16LE);
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

  private static class CachedDistributions {
    private final @NotNull List<WSLDistribution> myInstalledDistributions;
    private final long myExternalChangesCount;

    private CachedDistributions(@NotNull List<WSLDistribution> installedDistributions) {
      myInstalledDistributions = installedDistributions;
      myExternalChangesCount = getCurrentExternalChangesCount();
    }

    public boolean isUpToDate() {
      return getCurrentExternalChangesCount() == myExternalChangesCount;
    }

    private static long getCurrentExternalChangesCount() {
      return SaveAndSyncHandler.getInstance().getExternalChangesTracker().getModificationCount();
    }
  }
}

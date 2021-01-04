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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
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

  private volatile CachedDistributions myInstalledDistributions;
  private final Map<String, WSLDistribution> myMsIdToDistributionCache = ContainerUtil.createConcurrentWeakMap();

  @Override
  public void dispose() {
    myMsIdToDistributionCache.clear();
    myInstalledDistributions = null;
  }

  /**
   * @return list of installed WSL distributions by parsing output of `wsl.exe -l`. Please call it
   * on a pooled thread and outside of the read action as it runs a process under the hood.
   * @see #getInstalledDistributionsFuture
   */
  public @NotNull List<WSLDistribution> getInstalledDistributions() {
    CachedDistributions cachedDistributions = myInstalledDistributions;
    if (cachedDistributions != null && cachedDistributions.isUpToDate()) {
      return cachedDistributions.myInstalledDistributions;
    }
    myInstalledDistributions = null;
    synchronized (LOCK) {
      cachedDistributions = myInstalledDistributions;
      if (cachedDistributions == null) {
        cachedDistributions = new CachedDistributions(loadInstalledDistributions());
        myInstalledDistributions = cachedDistributions;
      }
    }
    return cachedDistributions.myInstalledDistributions;
  }

  public @NotNull CompletableFuture<List<WSLDistribution>> getInstalledDistributionsFuture() {
    CachedDistributions cachedDistributions = myInstalledDistributions;
    if (cachedDistributions != null && cachedDistributions.isUpToDate()) {
      return CompletableFuture.completedFuture(cachedDistributions.myInstalledDistributions);
    }
    return CompletableFuture.supplyAsync(this::getInstalledDistributions, AppExecutorUtil.getAppExecutorService());
  }

  /**
   * @return {@link WSLDistribution} instance by WSL distribution name. Please note that
   * the returned distribution is not guaranteed to be installed actually (for that check if the distribution is contained in
   * {@link #getInstalledDistributions}).
   * @param msId WSL distribution name, same as produced by `wsl.exe -l`
   */
  public @NotNull WSLDistribution getOrCreateDistributionByMsId(@NonNls @NotNull String msId) {
    if (msId.isEmpty()) {
      throw new IllegalStateException("WSL msId is empty");
    }
    // reuse previously created WSLDistribution instances to avoid re-calculating Host IP / WSL IP
    WSLDistribution d = myMsIdToDistributionCache.get(msId);
    if (d != null) {
      return d;
    }
    synchronized (myMsIdToDistributionCache) {
      d = myMsIdToDistributionCache.get(msId);
      if (d == null) {
        d = new WSLDistribution(msId);
        myMsIdToDistributionCache.put(msId, d);
      }
    }
    return d;
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

    return Pair.create(wslPath, getOrCreateDistributionByMsId(distName));
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

  private @NotNull List<WSLDistribution> loadInstalledDistributions() {
    checkEdtAndReadAction();
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

  private @NotNull Pair<GeneralCommandLine, List<WSLDistribution>> doFetchDistributionsFromWslCli() throws IOException {
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
    List<@NlsSafe String> msIds = ContainerUtil.filter(output.getStdoutLines(), distribution -> {
      return !INTERNAL_DISTRIBUTIONS.contains(distribution);
    });
    return Pair.create(commandLine, ContainerUtil.map(msIds, this::getOrCreateDistributionByMsId));
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

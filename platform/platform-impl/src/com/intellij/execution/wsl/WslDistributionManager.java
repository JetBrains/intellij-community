// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class WslDistributionManager implements Disposable {

  static final Logger LOG = Logger.getInstance(WslDistributionManager.class);
  private static final Object LOCK = new Object();

  public static @NotNull WslDistributionManager getInstance() {
    return ApplicationManager.getApplication().getService(WslDistributionManager.class);
  }

  private volatile CachedDistributions myInstalledDistributions;
  private final Map<String, WSLDistribution> myMsIdToDistributionCache = ContainerUtil.createConcurrentWeakMap(
    CaseInsensitiveStringHashingStrategy.INSTANCE);

  @Override
  public void dispose() {
    myMsIdToDistributionCache.clear();
    myInstalledDistributions = null;
  }

  /**
   * @return not-null if installed distribution list is up-to-date; otherwise, return null and initialize update in background.
   */
  public @Nullable List<WSLDistribution> getCachedInstalledDistributions() {
    return getInstalledDistributionsFuture().getNow(null);
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
   * the returned distribution is not guaranteed to be installed (for that, check if the distribution is contained in
   * {@link #getInstalledDistributions}).
   * @param msId WSL distribution name, same as produced by `wsl.exe -l`
   */
  public @NotNull WSLDistribution getOrCreateDistributionByMsId(@NonNls @NotNull String msId) {
    return getOrCreateDistributionByMsId(msId, false);
  }

  private @NotNull WSLDistribution getOrCreateDistributionByMsId(@NonNls @NotNull String msId, boolean overrideCaseInsensitively) {
    if (msId.isEmpty()) {
      throw new IllegalArgumentException("WSL msId is empty");
    }
    // reuse previously created WSLDistribution instances to avoid re-calculating Host IP / WSL IP
    WSLDistribution d = myMsIdToDistributionCache.get(msId);
    if (d == null || (overrideCaseInsensitively && !d.getMsId().equals(msId))) {
      synchronized (myMsIdToDistributionCache) {
        d = myMsIdToDistributionCache.get(msId);
        if (d == null || (overrideCaseInsensitively && !d.getMsId().equals(msId))) {
          d = new WSLDistribution(msId);
          myMsIdToDistributionCache.put(msId, d);
        }
      }
    }
    return d;
  }

  public static boolean isWslPath(@NotNull String path) {
    return FileUtil.toSystemDependentName(path).startsWith(WSLDistribution.UNC_PREFIX);
  }

  private @NotNull List<WSLDistribution> loadInstalledDistributions() {
    return ContainerUtil.map(loadInstalledDistributionMsIds(), (msId) -> {
      return getOrCreateDistributionByMsId(msId, true);
    });
  }

  protected abstract @NotNull List<String> loadInstalledDistributionMsIds();

  private static class CachedDistributions {
    private final @NotNull List<WSLDistribution> myInstalledDistributions;
    private final long myExternalChangesCount;

    private CachedDistributions(@NotNull List<WSLDistribution> installedDistributions) {
      myInstalledDistributions = List.copyOf(installedDistributions);
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

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
  private volatile List<WSLDistribution> myLastInstalledDistributions;
  private final Map<String, WSLDistribution> myMsIdToDistributionCache = CollectionFactory.createConcurrentWeakCaseInsensitiveMap();

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
   * @return last loaded list of installed distributions or {@code null} if it hasn't been loaded yet.
   * Please note the returned list might be out-of-date. To get the up-to-date list, please use {@link #getInstalledDistributionsFuture}.
   */
  public @Nullable List<WSLDistribution> getLastInstalledDistributions() {
    return myLastInstalledDistributions;
  }

  /**
   * @return list of installed WSL distributions by parsing output of `wsl.exe -l`. Please call it
   * on a pooled thread and outside the read action as it runs a process under the hood.
   * @see #getInstalledDistributionsFuture
   */
  public @NotNull List<WSLDistribution> getInstalledDistributions() {
    if (!isAvailable()) return List.of();
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
        myLastInstalledDistributions = cachedDistributions.myInstalledDistributions;
      }
    }
    return cachedDistributions.myInstalledDistributions;
  }

  public @NotNull CompletableFuture<List<WSLDistribution>> getInstalledDistributionsFuture() {
    if (!isAvailable()) return CompletableFuture.completedFuture(List.of());
    CachedDistributions cachedDistributions = myInstalledDistributions;
    if (cachedDistributions != null && cachedDistributions.isUpToDate()) {
      return CompletableFuture.completedFuture(cachedDistributions.myInstalledDistributions);
    }
    return CompletableFuture.supplyAsync(this::getInstalledDistributions, AppExecutorUtil.getAppExecutorService());
  }

  protected boolean isAvailable() {
    return WSLUtil.isSystemCompatible();
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

  /**
   * @deprecated use {@link WslPath#isWslUncPath(String)} instead
   */
  @Deprecated
  public static boolean isWslPath(@NotNull String path) {
    return WslPath.isWslUncPath(path);
  }

  private @NotNull List<WSLDistribution> loadInstalledDistributions() {
    if (!isWslExeSupported()) {
      //noinspection deprecation
      return WSLUtil.getAvailableDistributions();
    }

    // we assume that after "2004" Windows release wsl.exe and all required flags are available
    if (Registry.is("wsl.list.prefer.verbose.output", true)) {
      try {
        final var result = loadInstalledDistributionsWithVersions();
        return ContainerUtil.map(result, data -> {
          final WSLDistribution distribution = getOrCreateDistributionByMsId(data.getDistributionName(), true);
          distribution.setVersion(data.getVersion());
          return distribution;
        });
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      catch (IllegalStateException e) {
        LOG.error(e);
      }
    }
    // fallback: using loadInstalledDistributionMsIds in case of execution exception or parsing error
    return ContainerUtil.map(loadInstalledDistributionMsIds(), (msId) -> {
      return getOrCreateDistributionByMsId(msId, true);
    });
  }

  protected boolean isWslExeSupported() {
    Long windowsBuild = SystemInfo.getWinBuildNumber();
    if (windowsBuild != null && windowsBuild > 0 && windowsBuild < 19041) {
      WSLUtil.WSLToolFlags wslTool = WSLUtil.getWSLToolFlags();
      return wslTool != null && (wslTool.isVerboseFlagAvailable || wslTool.isQuietFlagAvailable);
    }
    return true;
  }

  protected abstract @NotNull List<String> loadInstalledDistributionMsIds();

  /**
   * @throws IOException if an execution error occurs
   * @throws IllegalStateException if a parsing error occurs
   */
  public abstract @NotNull List<WslDistributionAndVersion> loadInstalledDistributionsWithVersions()
    throws IOException, IllegalStateException;

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

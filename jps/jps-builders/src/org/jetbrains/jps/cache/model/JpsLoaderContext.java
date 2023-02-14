package org.jetbrains.jps.cache.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.cache.client.JpsNettyClient;

import java.util.Map;

public final class JpsLoaderContext {
  private static final Logger LOG = Logger.getInstance(JpsLoaderContext.class);
  private final String commitId;
  private final int totalExpectedDownloads;
  private final JpsNettyClient nettyClient;
  private final CanceledStatus canceledStatus;
  private final Map<String, Map<String, BuildTargetState>> commitSourcesState;
  private final Map<String, Map<String, BuildTargetState>> currentSourcesState;

  private JpsLoaderContext(int totalExpectedDownloads,
                           @NotNull CanceledStatus canceledStatus,
                           @NotNull String commitId, @NotNull JpsNettyClient nettyClient,
                           @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                           @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    this.commitId = commitId;
    this.nettyClient = nettyClient;
    this.canceledStatus = canceledStatus;
    this.commitSourcesState = commitSourcesState;
    this.currentSourcesState = currentSourcesState;
    this.totalExpectedDownloads = totalExpectedDownloads;
  }

  @NotNull
  public String getCommitId() {
    return commitId;
  }

  public void sendDescriptionStatusMessage(@NotNull String message) {
    nettyClient.sendDescriptionStatusMessage(message);
  }

  public @NotNull JpsNettyClient getNettyClient() {
    return nettyClient;
  }

  @NotNull
  public Map<String, Map<String, BuildTargetState>> getCommitSourcesState() {
    return commitSourcesState;
  }

  @Nullable
  public Map<String, Map<String, BuildTargetState>> getCurrentSourcesState() {
    return currentSourcesState;
  }

  public int getTotalExpectedDownloads() {
    return totalExpectedDownloads;
  }

  public void checkCanceled() throws ProcessCanceledException {
    if (canceledStatus.isCanceled()) {
      LOG.info("JPS Caches download process canceled");
      throw new ProcessCanceledException();
    }
  }

  public static JpsLoaderContext createNewContext(int totalExpectedDownloads,
                                                  @NotNull CanceledStatus canceledStatus,
                                                  @NotNull String commitId, @NotNull JpsNettyClient nettyClient,
                                                  @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                                  @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return new JpsLoaderContext(totalExpectedDownloads, canceledStatus, commitId, nettyClient, commitSourcesState, currentSourcesState);
  }
}

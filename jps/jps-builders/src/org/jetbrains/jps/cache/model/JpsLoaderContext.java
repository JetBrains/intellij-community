package org.jetbrains.jps.cache.model;

import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.cache.client.JpsNettyClient;

import java.util.Map;
import java.util.UUID;

public final class JpsLoaderContext {
  private final String commitId;
  private final JpsNettyClient nettyClient;
  private final Map<String, Map<String, BuildTargetState>> commitSourcesState;
  private final Map<String, Map<String, BuildTargetState>> currentSourcesState;

  private JpsLoaderContext(@NotNull String commitId, @NotNull JpsNettyClient nettyClient,
                           @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                           @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    this.commitId = commitId;
    this.nettyClient = nettyClient;
    this.commitSourcesState = commitSourcesState;
    this.currentSourcesState = currentSourcesState;
  }

  @NotNull
  public String getCommitId() {
    return commitId;
  }

  public void sendMainStatusMessage(@NotNull String message) {
    nettyClient.sendMainStatusMessage(message);
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

  public static JpsLoaderContext createNewContext(@NotNull String commitId, @NotNull JpsNettyClient nettyClient,
                                                  @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                                  @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return new JpsLoaderContext(commitId, nettyClient, commitSourcesState, currentSourcesState);
  }
}

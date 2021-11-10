// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.client;

import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;

import java.util.UUID;

public class JpsNettyClient {
  private final UUID sessionId;
  private final Channel channel;

  public JpsNettyClient(@NotNull Channel channel, @NotNull UUID sessionId) {
    this.channel = channel;
    this.sessionId = sessionId;
  }

  public void sendMainStatusMessage(@NotNull String message) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessage(message)));
  }

  public void sendDescriptionStatusMessage(@NotNull String message) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessage2(message)));
  }

  public void sendLatestDownloadCommitMessage(@NotNull String latestDownloadCommit) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createLatestDownloadCommitMessage(latestDownloadCommit)));
  }

  public void requestLatestDownloadCommitMessage() {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createLatestDownloadCommitRequest()));
  }

  public void requestRepositoryCommits(@NotNull String latestCommit) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createRequestRepositoryCommits(latestCommit)));
  }

  public void requestAuthToken() {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createAuthTokenRequest()));
  }
}

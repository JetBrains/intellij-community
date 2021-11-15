// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.client;

import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class JpsNettyClient {
  private final UUID sessionId;
  private final Channel channel;
  private final AtomicInteger currentDownloadsCount;

  public JpsNettyClient(@NotNull Channel channel, @NotNull UUID sessionId) {
    this.channel = channel;
    this.sessionId = sessionId;
    this.currentDownloadsCount = new AtomicInteger();
  }

  public void sendMainStatusMessage(@NotNull String message) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessage(message)));
  }

  public void sendDescriptionStatusMessage(@NotNull String message) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessage2(message)));
  }

  public void sendDescriptionStatusMessage(@NotNull String message, int expectedDownloads) {
    int currentDownloads = currentDownloadsCount.incrementAndGet();
    if (expectedDownloads == 0) {
      channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessage2(message)));
    } else {
      channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessageWithProgress(message, (float)currentDownloads / expectedDownloads)));
    }
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

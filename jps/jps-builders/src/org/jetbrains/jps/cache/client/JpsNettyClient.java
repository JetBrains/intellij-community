// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.client;

import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
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

  public void sendDescriptionStatusMessage(@NotNull String message) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessage(message)));
  }

  public void sendDescriptionStatusMessage(@NotNull String message, int expectedDownloads) {
    int currentDownloads = currentDownloadsCount.incrementAndGet();
    if (expectedDownloads == 0) {
      channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessage(message)));
    } else {
      int doubleExpectedDownloads = expectedDownloads * 2 + 1000;
      channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCacheDownloadMessageWithProgress(message, (float)currentDownloads / doubleExpectedDownloads)));
    }
  }

  public void sendDownloadStatisticMessage(@NotNull String latestDownloadCommit, long decompressionTimeBytesPesSec,
                                           long deletionTimeBytesPerSec) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createSaveDownloadStatisticMessage(latestDownloadCommit,
                                                                                                                    decompressionTimeBytesPesSec,
                                                                                                                    deletionTimeBytesPerSec)));
  }

  public void requestRepositoryCommits(@NotNull String latestCommit) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createRequestRepositoryCommitsAndStatistics(latestCommit)));
  }

  public void requestAuthToken() {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createAuthTokenRequest()));
  }

  public void saveLatestBuiltCommit() {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createSaveLatestBuiltCommitMessage()));
  }
}

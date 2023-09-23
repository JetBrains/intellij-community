// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.client;

import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class JpsNettyClient {
  private final UUID mySessionId;
  private final Channel myChannel;
  private final AtomicInteger currentDownloadsCount;

  public JpsNettyClient(@NotNull Channel channel, @NotNull UUID sessionId) {
    this.myChannel = channel;
    this.mySessionId = sessionId;
    this.currentDownloadsCount = new AtomicInteger();
  }

  public void sendDescriptionStatusMessage(@NotNull String message) {
    myChannel.writeAndFlush(CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createCacheDownloadMessage(message)));
  }

  public void sendDescriptionStatusMessage(@NotNull String message, int expectedDownloads) {
    int currentDownloads = currentDownloadsCount.incrementAndGet();
    if (expectedDownloads == 0) {
      myChannel.writeAndFlush(CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createCacheDownloadMessage(message)));
    } else {
      int doubleExpectedDownloads = expectedDownloads * 2 + 1000;
      myChannel.writeAndFlush(CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createCacheDownloadMessageWithProgress(message, (float)currentDownloads / doubleExpectedDownloads)));
    }
  }

  public void sendDownloadStatisticMessage(@NotNull String latestDownloadCommit, long decompressionTimeBytesPesSec,
                                           long deletionTimeBytesPerSec) {
    myChannel.writeAndFlush(CmdlineProtoUtil.toMessage(mySessionId, CmdlineProtoUtil.createSaveDownloadStatisticMessage(latestDownloadCommit,
                                                                                                                        decompressionTimeBytesPesSec,
                                                                                                                        deletionTimeBytesPerSec)));
  }

  public static void saveLatestBuiltCommit(@NotNull Channel channel, @NotNull UUID sessionId) {
    channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createSaveLatestBuiltCommitMessage()));
  }
}

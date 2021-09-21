// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.client;

import com.intellij.openapi.diagnostic.Logger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.cache.loader.JpsOutputLoaderManager;

import java.util.UUID;

public class JpsNettyClient {
  private static final Logger LOG = Logger.getInstance(JpsNettyClient.class);
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

  public void requestRepositoryCommits(@NotNull String latestCommit) {
    try {
      ChannelFuture channelFuture = channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createRequestRepositoryCommits(latestCommit)));
      channelFuture.await();
    }
    catch (InterruptedException e) {
      LOG.warn("Can't request repository commits", e);
    }
  }

  public void requestAuthToken() {
    try {
      ChannelFuture channelFuture = channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createAuthTokenRequest()));
      channelFuture.await();
    }
    catch (InterruptedException e) {
      LOG.warn("Can't request authentication token", e);
    }
  }
}

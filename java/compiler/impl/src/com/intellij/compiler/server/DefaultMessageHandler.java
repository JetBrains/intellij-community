// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.compiler.cache.CompilerCacheLoadingSettings;
import com.intellij.compiler.cache.git.GitRepositoryUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.UUID;

public abstract class DefaultMessageHandler implements BuilderMessageHandler {
  private static final Logger LOG = Logger.getInstance(DefaultMessageHandler.class);
  private final Project myProject;

  protected DefaultMessageHandler(Project project) {
    myProject = project;
  }

  @Override
  public void buildStarted(@NotNull UUID sessionId) {
  }

  @Override
  public final void handleBuildMessage(final Channel channel, final UUID sessionId, final CmdlineRemoteProto.Message.BuilderMessage msg) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (msg.getType()) {
      case BUILD_EVENT -> {
        final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event = msg.getBuildEvent();
        if (event.getEventType() == CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type.CUSTOM_BUILDER_MESSAGE &&
            event.hasCustomBuilderMessage()) {
          final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.CustomBuilderMessage message = event.getCustomBuilderMessage();
          if (!myProject.isDisposed()) {
            myProject.getMessageBus().syncPublisher(CustomBuilderMessageHandler.TOPIC).messageReceived(
              sessionId, message.getBuilderId(), message.getMessageType(), message.getMessageText()
            );
          }
        }
        handleBuildEvent(sessionId, event);
      }
      case COMPILE_MESSAGE -> {
        CmdlineRemoteProto.Message.BuilderMessage.CompileMessage compileMessage = msg.getCompileMessage();
        handleCompileMessage(sessionId, compileMessage);
        if (compileMessage.getKind() == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.INTERNAL_BUILDER_ERROR) {
          LOG.error("Internal build error:\n" + compileMessage.getText());
        }
      }
      case CACHE_DOWNLOAD_MESSAGE -> {
        CmdlineRemoteProto.Message.BuilderMessage.CacheDownloadMessage cacheDownloadMessage = msg.getCacheDownloadMessage();
        ProgressIndicator progressIndicator = getProgressIndicator();
        progressIndicator.setIndeterminate(false);
        // Used only in internal development thus shouldn't be localized
        @NlsSafe String descriptionText = cacheDownloadMessage.getDescriptionText();
        progressIndicator.setText(descriptionText);
        if (cacheDownloadMessage.hasDone()) {
          progressIndicator.setFraction(cacheDownloadMessage.getDone());
        }
      }
      case SAVE_LATEST_DOWNLOAD_STATISTIC_MESSAGE -> {
        CmdlineRemoteProto.Message.BuilderMessage.CommitAndDownloadStatistics downloadStatisticsMessage =
          msg.getCommitAndDownloadStatistics();
        GitRepositoryUtil.saveLatestDownloadedCommit(downloadStatisticsMessage.getCommit());
        CompilerCacheLoadingSettings.saveApproximateDecompressionSpeed(downloadStatisticsMessage.getDecompressionSpeed());
        CompilerCacheLoadingSettings.saveApproximateDeletionSpeed(downloadStatisticsMessage.getDeletionSpeed());
      }
      case SAVE_LATEST_BUILT_COMMIT_MESSAGE -> GitRepositoryUtil.saveLatestBuiltMasterCommit(myProject);
      case CONSTANT_SEARCH_TASK -> {
        // ignored, because the functionality is deprecated
      }
    }
  }

  protected abstract void handleCompileMessage(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message);

  protected abstract void handleBuildEvent(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event);

  public abstract @NotNull ProgressIndicator getProgressIndicator();
}

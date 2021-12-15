// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.server;

import com.intellij.compiler.cache.client.JpsServerAuthUtil;
import com.intellij.compiler.cache.git.GitRepositoryUtil;
import com.intellij.compiler.cache.statistic.JpsCacheLoadingStats;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.List;
import java.util.Map;
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
      case BUILD_EVENT:
        final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event = msg.getBuildEvent();
        if (event.getEventType() == CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type.CUSTOM_BUILDER_MESSAGE && event.hasCustomBuilderMessage()) {
          final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.CustomBuilderMessage message = event.getCustomBuilderMessage();
          if (!myProject.isDisposed()) {
            myProject.getMessageBus().syncPublisher(CustomBuilderMessageHandler.TOPIC).messageReceived(
              message.getBuilderId(), message.getMessageType(), message.getMessageText()
            );
          }
        }
        handleBuildEvent(sessionId, event);
        break;
      case COMPILE_MESSAGE:
        CmdlineRemoteProto.Message.BuilderMessage.CompileMessage compileMessage = msg.getCompileMessage();
        handleCompileMessage(sessionId, compileMessage);
        if (compileMessage.getKind() == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.INTERNAL_BUILDER_ERROR) {
          LOG.error("Internal build error:\n" + compileMessage.getText());
        }
        break;
      case AUTH_TOKEN_REQUEST:
        Map<String, String> headers = JpsServerAuthUtil.getRequestHeaders(myProject);
        channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createRequestParamsCommand(headers)));
        break;
      case CACHE_DOWNLOAD_MESSAGE:
        CmdlineRemoteProto.Message.BuilderMessage.CacheDownloadMessage cacheDownloadMessage = msg.getCacheDownloadMessage();
        ProgressIndicator progressIndicator = getProgressIndicator();
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText(cacheDownloadMessage.getDescriptionText());
        if (cacheDownloadMessage.hasDone()) {
          progressIndicator.setFraction(cacheDownloadMessage.getDone());
        }
        break;
      case REPOSITORY_COMMITS_REQUEST:
        CmdlineRemoteProto.Message.BuilderMessage.CommitAndDownloadStatistics downloadStatistics = msg.getCommitAndDownloadStatistics();
        String latestCommit = downloadStatistics.getCommit();
        List<String> repositoryCommits = GitRepositoryUtil.fetchRepositoryCommits(myProject, latestCommit);

        long deletionSpeed = 0;
        long decompressionSpeed = 0;
        String latestDownloadedCommit = "";
        String nearestRemoteMasterCommit = "";
        if (latestCommit.isEmpty()) {
          nearestRemoteMasterCommit = GitRepositoryUtil.getLatestBuiltMasterCommitId();
          latestDownloadedCommit = GitRepositoryUtil.getLatestDownloadedCommit();
          decompressionSpeed = JpsCacheLoadingStats.getApproximateDecompressionSpeed();
          deletionSpeed = JpsCacheLoadingStats.getApproximateDeletionSpeed();
        }
        CmdlineRemoteProto.Message.ControllerMessage message = CmdlineProtoUtil.createRepositoryCommitsMessage(repositoryCommits,
                                                                                                               nearestRemoteMasterCommit,
                                                                                                               latestDownloadedCommit,
                                                                                                               deletionSpeed,
                                                                                                               decompressionSpeed);
        channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, message));
        break;
      case SAVE_LATEST_DOWNLOAD_STATISTIC_MESSAGE:
        CmdlineRemoteProto.Message.BuilderMessage.CommitAndDownloadStatistics downloadStatisticsMessage = msg.getCommitAndDownloadStatistics();
        GitRepositoryUtil.saveLatestDownloadedCommit(downloadStatisticsMessage.getCommit());
        JpsCacheLoadingStats.saveApproximateDecompressionSpeed(downloadStatisticsMessage.getDecompressionSpeed());
        JpsCacheLoadingStats.saveApproximateDeletionSpeed(downloadStatisticsMessage.getDeletionSpeed());
        break;
      case SAVE_LATEST_BUILT_COMMIT_MESSAGE:
        GitRepositoryUtil.saveLatestBuiltMasterCommit(myProject);
        break;
      case CONSTANT_SEARCH_TASK:
        // ignored, because the functionality is deprecated
        break;
    }
  }

  protected abstract void handleCompileMessage(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message);

  protected abstract void handleBuildEvent(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event);

  @NotNull
  public abstract ProgressIndicator getProgressIndicator();
}

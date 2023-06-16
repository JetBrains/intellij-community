// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache;

import com.intellij.compiler.CompilerConfigurationSettings;
import com.intellij.compiler.cache.client.CompilerCacheServerAuthService;
import com.intellij.compiler.cache.client.CompilerCachesServerClient;
import com.intellij.compiler.cache.git.GitCommitsIterator;
import com.intellij.compiler.cache.git.GitRepositoryUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.Map;
import java.util.Set;

import static org.jetbrains.jps.cache.JpsCachesLoaderUtil.INTELLIJ_REPO_NAME;

public final class CompilerCacheConfigurator {
  private static final Logger LOG = Logger.getInstance(CompilerCacheConfigurator.class);

  public static @Nullable CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings getCacheDownloadSettings(@NotNull Project project) {
    if (!Registry.is("compiler.process.use.portable.caches")) return null;
    String serverUrl = getServerUrl(project);
    if (serverUrl == null || !CompilerCacheStartupActivity.isLineEndingsConfiguredCorrectly()) return null;

    Map<String, String> authHeaders = CompilerCacheServerAuthService.getInstance(project).getRequestHeaders(true);
    if (authHeaders.isEmpty()) return null;
    Pair<String, Integer> commit = getCommitToDownload(project, serverUrl, CompilerCacheLoadingSettings.getForceUpdateValue());
    if (commit == null) return null;

    CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings.Builder builder =
      CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings.newBuilder();
    builder.setServerUrl(serverUrl);
    builder.setDownloadCommit(commit.first);
    builder.setCommitsCountLatestBuild(commit.second);
    builder.putAllAuthHeaders(authHeaders);
    builder.setDecompressionSpeed(CompilerCacheLoadingSettings.getApproximateDecompressionSpeed());
    builder.setDeletionSpeed(CompilerCacheLoadingSettings.getApproximateDeletionSpeed());
    builder.setForceDownload(CompilerCacheLoadingSettings.getForceUpdateValue());
    builder.setDisableDownload(CompilerCacheLoadingSettings.getDisableUpdateValue());
    builder.setCleanupAsynchronously(CompilerCacheLoadingSettings.getCleanupAsynchronouslyValue());
    builder.setMaxDownloadDuration(CompilerCacheLoadingSettings.getMaxDownloadDuration());
    return builder.build();
  }

  public static boolean isServerUrlConfigured(@NotNull Project project) {
    return getServerUrl(project) != null;
  }

  @Nullable
  private static Pair<String, Integer> getCommitToDownload(@NotNull Project project, @NotNull String serverUrl, boolean forceUpdate) {
    Map<String, Set<String>> availableCommitsPerRemote = CompilerCachesServerClient.getCacheKeysPerRemote(project, serverUrl);
    GitCommitsIterator commitsIterator = new GitCommitsIterator(project, INTELLIJ_REPO_NAME);
    String latestDownloadedCommit = GitRepositoryUtil.getLatestDownloadedCommit();
    String latestBuiltCommit = GitRepositoryUtil.getLatestBuiltMasterCommitId();
    Set<String> availableCommitsForRemote = availableCommitsPerRemote.get(commitsIterator.getRemote());
    if (availableCommitsForRemote == null) {
      LOG.warn("Not found any caches for the remote: " + commitsIterator.getRemote());
      return null;
    }

    int commitsBehind = 0;
    int commitsCountBetweenCompilation = 0;
    String commitToDownload = "";
    boolean latestBuiltCommitFound = false;
    while (commitsIterator.hasNext()) {
      String commitId = commitsIterator.next();
      if (commitId.equals(latestBuiltCommit) && !latestBuiltCommitFound) {
        latestBuiltCommitFound = true;
      }
      if (!latestBuiltCommitFound) {
        commitsCountBetweenCompilation++;
      }
      if (availableCommitsForRemote.contains(commitId) && commitToDownload.isEmpty()) {
        commitToDownload = commitId;
      }
      if (commitToDownload.isEmpty()) {
        commitsBehind++;
      }

      if (latestBuiltCommitFound && !commitToDownload.isEmpty()) break;
    }

    if (!forceUpdate && commitsCountBetweenCompilation == 0) {
      LOG.warn("No new commits since last success compilation");
      return null;
    }
    if (!availableCommitsForRemote.contains(commitToDownload)) {
      LOG.warn("Not found any caches for the latest commits in the branch");
      return null;
    }
    LOG.info("Project contains " + commitsCountBetweenCompilation + " non compiled commits. Cache will be downloaded for " + commitsBehind + " commits before the current master." +
             " The rest commits will be build locally. Commit hash for download: " + commitToDownload);
    if (!forceUpdate && commitsBehind >= commitsCountBetweenCompilation) {
      LOG.info("Caches available for already compiled commit. Skipping download");
      return null;
    }
    if (!forceUpdate && commitToDownload.equals(latestDownloadedCommit)) {
      LOG.info("The system contains up-to-date caches");
      return null;
    }
    if (!forceUpdate && commitToDownload.equals(latestBuiltCommit)) {
      LOG.info("Commit already compiled, thus caches for it won't be download");
      return null;
    }
    return Pair.create(commitToDownload, commitsCountBetweenCompilation);
  }

  private static @Nullable String getServerUrl(@NotNull Project project) {
    return CompilerConfigurationSettings.Companion.getInstance(project).getCacheServerUrl();
  }
}

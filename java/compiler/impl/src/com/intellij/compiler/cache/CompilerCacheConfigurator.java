// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache;

import com.intellij.compiler.cache.client.CompilerCacheServerAuthUtil;
import com.intellij.compiler.cache.client.CompilerCachesServerClient;
import com.intellij.compiler.cache.git.GitCommitsIterator;
import com.intellij.compiler.cache.git.GitRepositoryUtil;
import com.intellij.compiler.cache.statistic.CompilerCacheLoadingStats;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.jps.cache.JpsCachesLoaderUtil.INTELLIJ_REPO_NAME;

public class CompilerCacheConfigurator {
  private static final Logger LOG = Logger.getInstance(CompilerCacheConfigurator.class);
  public static final CompilerCacheConfigurator INSTANCE = new CompilerCacheConfigurator();
  private final String serverUrl;

  private CompilerCacheConfigurator() {
    byte[] decodedBytes = Base64.getDecoder().decode("aHR0cHM6Ly9kMWxjNWs5bGVyZzZrbS5jbG91ZGZyb250Lm5ldA==");
    serverUrl = new String(decodedBytes, StandardCharsets.UTF_8);
  }

  public @Nullable CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings getCacheDownloadSettings(@NotNull Project project) {
    if (!Registry.is("compiler.process.use.portable.caches") ||
        !GitRepositoryUtil.isIntelliJRepository(project) ||
        !CompilerCacheStartupActivity.isLineEndingsConfiguredCorrectly()) return null;

    Map<String, String> authHeaders = CompilerCacheServerAuthUtil.getRequestHeaders(project, true);
    if (authHeaders.isEmpty()) return null;
    Pair<String, Integer> commit = getCommitToDownload(project);
    if (commit == null) return null;

    CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings.Builder builder =
      CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings.newBuilder();
    builder.setServerUrl(serverUrl);
    builder.setDownloadCommit(commit.first);
    builder.setCommitsCountLatestBuild(commit.second);
    builder.putAllAuthHeaders(authHeaders);
    builder.setDecompressionSpeed(CompilerCacheLoadingStats.getApproximateDecompressionSpeed());
    builder.setDeletionSpeed(CompilerCacheLoadingStats.getApproximateDeletionSpeed());
    return builder.build();
  }

  @Nullable
  private Pair<String, Integer> getCommitToDownload(@NotNull Project project) {
    Map<String, Set<String>> availableCommitsPerRemote = CompilerCachesServerClient.getCacheKeysPerRemote(project, serverUrl);
    GitCommitsIterator commitsIterator = new GitCommitsIterator(project, INTELLIJ_REPO_NAME);
    String latestDownloadedCommit = GitRepositoryUtil.getLatestDownloadedCommit();
    String latestBuiltCommit = GitRepositoryUtil.getLatestBuiltMasterCommitId();
    Set<String> availableCommitsForRemote = availableCommitsPerRemote.get(commitsIterator.getRemote());
    if (availableCommitsForRemote == null) {
      LOG.warn("Not found any caches for the latest commits in the branch");
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

    if (commitsCountBetweenCompilation == 0) {
      LOG.warn("No new commits since last success compilation");
      return null;
    }
    if (!availableCommitsForRemote.contains(commitToDownload)) {
      LOG.warn("Not found any caches for the latest commits in the branch");
      return null;
    }
    LOG.info("Non Compiled commits count: " + commitsCountBetweenCompilation + ". " + commitsBehind + " commits behind the master. " +
             "Commit to download: " + commitToDownload);
    if (commitToDownload.equals(latestDownloadedCommit)) {
      LOG.info("The system contains up-to-date caches");
      return null;
    }
    return Pair.create(commitToDownload, commitsCountBetweenCompilation);
  }
}

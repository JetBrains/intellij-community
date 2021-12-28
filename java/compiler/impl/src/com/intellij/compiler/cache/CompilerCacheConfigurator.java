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
import org.jetbrains.jps.builders.JpsBuildBundle;

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
    Pair<String, Integer> commit = getCommitToDownload(project);
    if (commit == null) return null;

    CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings.Builder builder =
      CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings.newBuilder();
    builder.setServerUrl(serverUrl);
    builder.setDownloadCommit(commit.first);
    builder.setCommitsCountLatestBuild(commit.second);
    builder.putAllAuthHeaders(CompilerCacheServerAuthUtil.getRequestHeaders(project));
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
      String message = JpsBuildBundle.message("progress.text.not.found.any.caches.for.latest.commits.in.branch");
      LOG.warn(message);
      return null;
    }

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

      if (latestBuiltCommitFound && !commitToDownload.isEmpty()) break;
    }

    if (commitsCountBetweenCompilation == 0) {
      String message = JpsBuildBundle.message("progress.text.no.commits.since.latest.compilation");
      LOG.warn(message);
      return null;
    }
    if (!availableCommitsForRemote.contains(commitToDownload)) {
      String message = JpsBuildBundle.message("progress.text.not.found.any.caches.for.latest.commits.in.branch");
      LOG.warn(message);
      return null;
    }
    LOG.info("Commits count between latest success compilation and current commit: " + commitsCountBetweenCompilation +
             ". Detected commit to download: " + commitToDownload);
    if (commitToDownload.equals(latestDownloadedCommit)) {
      String message = JpsBuildBundle.message("progress.text.system.contains.up.to.date.caches");
      LOG.info(message);
      return null;
    }
    return Pair.create(commitToDownload, commitsCountBetweenCompilation);
  }
}

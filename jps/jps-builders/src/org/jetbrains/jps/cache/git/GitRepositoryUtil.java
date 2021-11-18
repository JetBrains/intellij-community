package org.jetbrains.jps.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cache.client.JpsNettyClient;

import java.util.List;

public final class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance(GitRepositoryUtil.class);
  private static final Object myLock = new Object();
  private static String latestDownloadedCommit = "";

  private GitRepositoryUtil() {}

  //@NotNull
  //public static List<GitCommitsIterator> getCommitsIterator(@NotNull Project project, @NotNull Set<String> remoteUrlNames) {
  //  if (GitUtil.hasGitRepositories(project)) {
  //    return GitUtil.getRepositories(project).stream()
  //      .map(repo -> {
  //        Set<String> remoteUrls = repo.getRemotes().stream()
  //          .map(remote -> remote.getUrls())
  //          .flatMap(Collection::stream)
  //          .collect(Collectors.toSet());
  //        String matchedRemoteUrl = ContainerUtil.find(remoteUrls, remoteUrl -> remoteUrlNames.contains(getRemoteRepoName(remoteUrl)));
  //        if (matchedRemoteUrl == null) return null;
  //        return new GitCommitsIterator(project, repo, getRemoteRepoName(matchedRemoteUrl));
  //      }).filter(Objects::nonNull)
  //      .collect(Collectors.toList());
  //  }
  //  LOG.info("Project doesn't contain Git repository");
  //  return Collections.emptyList();
  //}

  //@Nullable
  //public static GitRepository getRepositoryByName(@NotNull Project project, @NotNull String repositoryName) {
  //  if (GitUtil.hasGitRepositories(project)) {
  //    return ContainerUtil.find(GitUtil.getRepositories(project), repo -> {
  //      for (GitRemote remote : repo.getRemotes()) {
  //        for (String remoteUrl : remote.getUrls()) {
  //          if (getRemoteRepoName(remoteUrl).equals(repositoryName)) return true;
  //        }
  //      }
  //      return false;
  //    });
  //  }
  //  LOG.info("Project doesn't contain Git repository");
  //  return null;
  //}

  //public static boolean isAutoCrlfSetRight(@NotNull GitRepository  gitRepository) {
  //  GitCommandResult result = Git.getInstance().config(gitRepository, GitConfigUtil.CORE_AUTOCRLF);
  //  String value = result.getOutputAsJoinedString();
  //  LOG.info("CRLF configuration for " + gitRepository + " project: " + value);
  //  return value.equalsIgnoreCase("input");
  //}

  public static String getRemoteRepoName(@NotNull String remoteUrl) {
    String[] splittedRemoteUrl = remoteUrl.split("/");
    return splittedRemoteUrl[splittedRemoteUrl.length - 1];
  }
}
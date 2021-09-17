//package org.jetbrains.jps.cache.git;
//
//import com.intellij.jps.cache.git.GitCommitsIterator;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.project.Project;
//import com.intellij.util.containers.ContainerUtil;
//import git4idea.GitUtil;
//import git4idea.commands.Git;
//import git4idea.commands.GitCommandResult;
//import git4idea.config.GitConfigUtil;
//import git4idea.repo.GitRemote;
//import git4idea.repo.GitRepository;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//public final class GitRepositoryUtil {
//  private static final Logger LOG = Logger.getInstance(GitRepositoryUtil.class);
//
//  private GitRepositoryUtil() {}
//
//  @NotNull
//  public static List<GitCommitsIterator> getCommitsIterator(@NotNull Project project, @NotNull Set<String> remoteUrlNames) {
//    if (GitUtil.hasGitRepositories(project)) {
//      return GitUtil.getRepositories(project).stream()
//        .map(repo -> {
//          Set<String> remoteUrls = repo.getRemotes().stream()
//            .map(remote -> remote.getUrls())
//            .flatMap(Collection::stream)
//            .collect(Collectors.toSet());
//          String matchedRemoteUrl = ContainerUtil.find(remoteUrls, remoteUrl -> remoteUrlNames.contains(getRemoteRepoName(remoteUrl)));
//          if (matchedRemoteUrl == null) return null;
//          return new GitCommitsIterator(project, repo, getRemoteRepoName(matchedRemoteUrl));
//        }).filter(Objects::nonNull)
//        .collect(Collectors.toList());
//    }
//    LOG.info("Project doesn't contain Git repository");
//    return Collections.emptyList();
//  }
//
//  @Nullable
//  public static GitRepository getRepositoryByName(@NotNull Project project, @NotNull String repositoryName) {
//    if (GitUtil.hasGitRepositories(project)) {
//      return ContainerUtil.find(GitUtil.getRepositories(project), repo -> {
//        for (GitRemote remote : repo.getRemotes()) {
//          for (String remoteUrl : remote.getUrls()) {
//            if (getRemoteRepoName(remoteUrl).equals(repositoryName)) return true;
//          }
//        }
//        return false;
//      });
//    }
//    LOG.info("Project doesn't contain Git repository");
//    return null;
//  }
//
//  public static String getRemoteRepoName(@NotNull String remoteUrl) {
//    String[] splittedRemoteUrl = remoteUrl.split("/");
//    return splittedRemoteUrl[splittedRemoteUrl.length - 1];
//  }
//
//  public static boolean isAutoCrlfSetRight(@NotNull GitRepository  gitRepository) {
//    GitCommandResult result = Git.getInstance().config(gitRepository, GitConfigUtil.CORE_AUTOCRLF);
//    String value = result.getOutputAsJoinedString();
//    LOG.info("CRLF configuration for " + gitRepository + " project: " + value);
//    return value.equalsIgnoreCase("input");
//  }
//}
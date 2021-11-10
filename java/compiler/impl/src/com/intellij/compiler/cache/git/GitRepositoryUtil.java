package com.intellij.compiler.cache.git;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance(GitRepositoryUtil.class);
  private static final String LATEST_COMMIT_ID = "JpsOutputLoaderManager.latestCommitId";
  private static final String INTELLIJ_REPO_NAME = "intellij.git";
  private static final int FETCH_SIZE = 100;

  private GitRepositoryUtil() {}

  public static boolean isIntelliJRepository(@NotNull Project project) {
    String projectBasePath = project.getBasePath();
    if (projectBasePath == null) return false;
    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(projectBasePath)
      .withExePath("git")
      .withParameters("remote")
      .withParameters("-v");

    AtomicBoolean result = new AtomicBoolean();
    try {
      OSProcessHandler handler = new OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8));
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (event.getExitCode() != 0) {
            LOG.warn("Couldn't fetch repository remote URL " + getOutput().getStderr());
          } else {
            result.set(getOutput().getStdout().contains(INTELLIJ_REPO_NAME));
          }
        }
      });
      handler.startNotify();
      handler.waitFor();
    }
    catch (ExecutionException e) {
      LOG.warn("Couldn't execute command for fetching remote URL", e);
    }
    return result.get();
  }

  public static @NotNull List<String> fetchRepositoryCommits(@NotNull Project project, @NotNull String latestCommit) {
    String projectBasePath = project.getBasePath();
    if (projectBasePath == null) return Collections.emptyList();

    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(projectBasePath)
      .withExePath("git")
      .withParameters("log")
      .withParameters("--format=\"%H\"");
    if (!latestCommit.isEmpty()) {
      commandLine.addParameter(latestCommit);
    }
    commandLine.withParameters("-n")
      .withParameters(Integer.toString(FETCH_SIZE));

    StringBuilder processOutput = new StringBuilder();
    try {
      OSProcessHandler handler = new OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8));
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (event.getExitCode() != 0) {
            LOG.warn("Couldn't fetch N commits from the current repository " + getOutput().getStderr());
          } else {
            processOutput.append(getOutput().getStdout());
          }
        }
      });
      handler.startNotify();
      handler.waitFor();
    }
    catch (ExecutionException e) {
      LOG.warn("Can't execute command for getting commit hashes", e);
    }
    String result = processOutput.toString();
    if (result.isEmpty()) return Collections.emptyList();
    return ContainerUtil.map(processOutput.toString().split("\n"), commit -> commit.substring(1, commit.length() - 1));
  }

  public static void saveLatestDownloadedCommit(@NotNull String latestDownloadedCommit) {
    PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, latestDownloadedCommit);
    LOG.info("Saving latest downloaded commit: " + latestDownloadedCommit);
  }

  public static @NotNull String getLatestDownloadedCommit() {
    String latestDownloadedCommit = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
    LOG.info("Getting latest downloaded commit: " + latestDownloadedCommit);
    if (latestDownloadedCommit == null) return "";
    return latestDownloadedCommit;
  }

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
}
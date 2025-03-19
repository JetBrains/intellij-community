// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance(GitRepositoryUtil.class);
  private static final String LATEST_COMMIT_ID = "JpsOutputLoaderManager.latestCommitId";
  private static final String LATEST_BUILT_MASTER_COMMIT_ID = "JpsOutputLoaderManager.latestBuiltMasterCommitId";
  private static final int FETCH_SIZE = 100;

  private GitRepositoryUtil() {}

  public static @Unmodifiable @NotNull List<String> fetchRepositoryCommits(@NotNull Project project, @NotNull String latestCommit) {
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
            LOG.warn("Couldn't fetch N commits from the current repository: " + getOutput().getStderr());
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

  private static @NotNull String getCurrentBranchName(@NotNull Project project) {
    String projectBasePath = project.getBasePath();
    if (projectBasePath == null) return "";
    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(projectBasePath)
      .withExePath("git")
      .withParameters("rev-parse")
      .withParameters("--abbrev-ref")
      .withParameters("HEAD");

    StringBuilder processOutput = new StringBuilder();
    try {
      OSProcessHandler handler = new OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8));
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (event.getExitCode() != 0) {
            LOG.warn("Couldn't fetch current branch name: " + getOutput().getStderr());
          } else {
            processOutput.append(getOutput().getStdout());
          }
        }
      });
      handler.startNotify();
      handler.waitFor();
    }
    catch (ExecutionException e) {
      LOG.warn("Couldn't execute command for getting current branch name", e);
    }
    String branch = processOutput.toString();
    LOG.debug("Git current branch name: " + branch);
    return branch.lines().findFirst().orElse("");
  }

  private static @NotNull String getNearestRemoteMasterBranchCommit(@NotNull Project project) {
    String projectBasePath = project.getBasePath();
    if (projectBasePath == null) return "";
    String branchName = getCurrentBranchName(project);
    if (branchName.isEmpty()) return "";
    String remoteName = getRemoteName(project, branchName);
    Optional<String> optionalRemoteName = remoteName.lines().findFirst();
    if (optionalRemoteName.isEmpty()) return "";

    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(projectBasePath)
      .withExePath("git")
      .withParameters("merge-base")
      .withParameters("HEAD")
      .withParameters(optionalRemoteName.get() + "/" + branchName);

    StringBuilder processOutput = new StringBuilder();
    try {
      OSProcessHandler handler = new OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8));
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (event.getExitCode() != 0) {
            LOG.warn("Couldn't get nearest commit from remote master: " + getOutput().getStderr());
          } else {
            processOutput.append(getOutput().getStdout());
          }
        }
      });
      handler.startNotify();
      handler.waitFor();
    }
    catch (ExecutionException e) {
      LOG.warn("Couldn't execute command for getting nearest commit from remote master", e);
    }
    return processOutput.toString().lines().findFirst().orElse("");
  }

  private static @NotNull String getRemoteName(@NotNull Project project, @NotNull String branchName) {
    String projectBasePath = project.getBasePath();
    if (projectBasePath == null) return "";
    String gitParam = "branch." + branchName + ".remote";
    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(projectBasePath)
      .withExePath("git")
      .withParameters("config")
      .withParameters("--get")
      .withParameters(gitParam);

    StringBuilder processOutput = new StringBuilder();
    try {
      OSProcessHandler handler = new OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8));
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (event.getExitCode() != 0) {
            LOG.warn("Couldn't fetch repository remote name: " + getOutput().getStderr());
          } else {
            processOutput.append(getOutput().getStdout());
          }
        }
      });
      handler.startNotify();
      handler.waitFor();
    }
    catch (ExecutionException e) {
      LOG.warn("Couldn't execute command for getting remote name", e);
    }
    String remote = processOutput.toString();
    LOG.debug("Git remote name for master: " + remote);
    return remote;
  }

  public static boolean isAutoCrlfSetRight(@NotNull Project project) {
    String projectBasePath = project.getBasePath();
    if (projectBasePath == null) return true;
    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkDirectory(projectBasePath)
      .withExePath("git")
      .withParameters("config")
      .withParameters("--get")
      .withParameters("core.autocrlf");

    StringBuilder processOutput = new StringBuilder();
    try {
      OSProcessHandler handler = new OSProcessHandler(commandLine.withCharset(StandardCharsets.UTF_8));
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          if (event.getExitCode() != 0) {
            LOG.warn("Couldn't fetch repository remote name: " + getOutput().getStderr());
          } else {
            processOutput.append(getOutput().getStdout());
          }
        }
      });
      handler.startNotify();
      handler.waitFor();
    }
    catch (ExecutionException e) {
      LOG.warn("Couldn't execute command for getting remote name", e);
    }
    String value = processOutput.toString().lines().findFirst().orElse("");
    LOG.info("CRLF configuration for " + project.getName() + " project: " + value);
    return value.equalsIgnoreCase("input");
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

  public static void saveLatestBuiltMasterCommit(@NotNull Project project) {
    String latestBuiltRemoteMasterCommit = getNearestRemoteMasterBranchCommit(project);
    if (latestBuiltRemoteMasterCommit.isEmpty()) return;
    PropertiesComponent.getInstance().setValue(LATEST_BUILT_MASTER_COMMIT_ID, latestBuiltRemoteMasterCommit);
    LOG.info("Saving latest built commit from remote master: " + latestBuiltRemoteMasterCommit);
  }

  public static @NotNull String getLatestBuiltMasterCommitId() {
    String latestBuiltCommit = PropertiesComponent.getInstance().getValue(LATEST_BUILT_MASTER_COMMIT_ID);
    LOG.info("Getting latest built remote master commit: " + latestBuiltCommit);
    if (latestBuiltCommit == null) return "";
    return latestBuiltCommit;
  }
}
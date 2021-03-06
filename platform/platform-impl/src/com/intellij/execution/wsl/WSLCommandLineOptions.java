// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WSLCommandLineOptions {

  static final @NlsSafe String DEFAULT_SHELL = "/bin/sh";

  private boolean myLaunchWithWslExe = true;
  private boolean myExecuteCommandInShell = true;
  private boolean myExecuteCommandInInteractiveShell = false;
  private boolean myExecuteCommandInLoginShell = false;
  private boolean mySudo = false;
  private String myRemoteWorkingDirectory;
  private boolean myPassEnvVarsUsingInterop = false;
  private final List<String> myInitShellCommands = new ArrayList<>();
  private boolean myExecuteCommandInDefaultShell = false;
  private @Nls @NotNull String myShellPath = DEFAULT_SHELL;

  public boolean isLaunchWithWslExe() {
    return myLaunchWithWslExe && Experiments.getInstance().isFeatureEnabled("wsl.execute.with.wsl.exe");
  }

  public @NotNull WSLCommandLineOptions setLaunchWithWslExe(boolean launchWithWslExe) {
    myLaunchWithWslExe = launchWithWslExe;
    return this;
  }

  public boolean isExecuteCommandInShell() {
    return myExecuteCommandInShell;
  }

  /**
   * Runs "wsl.exe --exec command" (requires launching with wsl.exe):
   * <pre>
   *     --exec, -e &lt;CommandLine&gt;
   *         Execute the specified command without using the default Linux shell.
   * </pre>
   */
  public @NotNull WSLCommandLineOptions setExecuteCommandInShell(boolean executeCommandInShell) {
    myExecuteCommandInShell = executeCommandInShell;
    return this;
  }


  public boolean isExecuteCommandInInteractiveShell() {
    return myExecuteCommandInInteractiveShell;
  }

  /**
   * runs wsl command in interactive shell (-i) parameter
   */
  public @NotNull WSLCommandLineOptions setExecuteCommandInInteractiveShell(boolean executeCommandInInteractiveShell) {
    myExecuteCommandInInteractiveShell = executeCommandInInteractiveShell;
    if (myExecuteCommandInInteractiveShell) myExecuteCommandInShell = true;
    return this;
  }


  public boolean isExecuteCommandInLoginShell() {
    return myExecuteCommandInLoginShell;
  }

  /**
   * runs wsl command in login shell (-l) parameter
   */
  public @NotNull WSLCommandLineOptions setExecuteCommandInLoginShell(boolean executeCommandInLoginShell) {
    myExecuteCommandInLoginShell = executeCommandInLoginShell;
    if (myExecuteCommandInLoginShell) myExecuteCommandInShell = true;
    return this;
  }

  boolean isExecuteCommandInDefaultShell() {
    return myExecuteCommandInDefaultShell;
  }

  /**
   * Executes command in default shell. Please note that shell expansion is enabled in this case,
   * so it's not suitable for running arbitrary command lines.
   *
   * @param executeCommandInDefaultShell
   * @return
   */
  @SuppressWarnings("SameParameterValue")
  @ApiStatus.Experimental
  @NotNull WSLCommandLineOptions setExecuteCommandInDefaultShell(boolean executeCommandInDefaultShell) {
    myExecuteCommandInDefaultShell = executeCommandInDefaultShell;
    return this;
  }

  public @Nls @NotNull String getShellPath() {
    return myShellPath;
  }

  public @NotNull WSLCommandLineOptions setShellPath(@Nls @NotNull String shellPath) {
    if (shellPath.isBlank()) {
      throw new AssertionError("Wrong shell: " + shellPath);
    }
    myShellPath = shellPath;
    return this;
  }

  public boolean isSudo() {
    return mySudo;
  }

  public @NotNull WSLCommandLineOptions setSudo(boolean sudo) {
    mySudo = sudo;
    return this;
  }

  public @Nullable String getRemoteWorkingDirectory() {
    return myRemoteWorkingDirectory;
  }

  /**
   * @param remoteWorkingDirectory Linux directory (e.g. {@code /home/user/stuff}) to be used as a working directory
   *                              when starting a process.
   * @return this instance
   */
  public @NotNull WSLCommandLineOptions setRemoteWorkingDirectory(@Nullable String remoteWorkingDirectory) {
    myRemoteWorkingDirectory = remoteWorkingDirectory;
    if (remoteWorkingDirectory != null && !remoteWorkingDirectory.startsWith("/")) {
      throw new AssertionError("Linux path was expected, but got " + remoteWorkingDirectory);
    }
    return this;
  }

  public boolean isPassEnvVarsUsingInterop() {
    return myPassEnvVarsUsingInterop;
  }

  /**
   * Enables passing environment variables to WSL process using environmental variable interoperability between Windows and WSL.
   * See <a href="https://devblogs.microsoft.com/commandline/share-environment-vars-between-wsl-and-windows/">Share Environment</a>.
   * @param passEnvVarsUsingInterop true to pass environment variables using interoperability
   */
  public @NotNull WSLCommandLineOptions setPassEnvVarsUsingInterop(boolean passEnvVarsUsingInterop) {
    myPassEnvVarsUsingInterop = passEnvVarsUsingInterop;
    return this;
  }

  public @NotNull List<String> getInitShellCommands() {
    return myInitShellCommands;
  }

  /**
   * Adds an initialize command that is applied only when executing in shell ({@link #isExecuteCommandInShell()} is true).
   * The initialize command is a linux command that runs before the main command.
   * If the initialize command fails (exit code != 0), the main command won't run.
   * For example, it can be used to setup environment before running the app.
   * 
   * @param initCommand a linux shell command (may contain shell builtin commands)
   */
  public @NotNull WSLCommandLineOptions addInitCommand(@NotNull String initCommand) {
    myInitShellCommands.add(initCommand);
    return this;
  }

  @Override
  public String toString() {
    return "launchWithWslExe=" + myLaunchWithWslExe +
           ", executeCommandInShell=" + myExecuteCommandInShell +
           ", sudo=" + mySudo +
           ", remoteWorkingDirectory='" + myRemoteWorkingDirectory + '\'' +
           ", passEnvVarsUsingInterop=" + myPassEnvVarsUsingInterop +
           ", initCommands=" + myInitShellCommands;
  }
}

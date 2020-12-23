// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.Experiments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WSLCommandLineOptions {

  private boolean myLaunchWithWslExe = true;
  private boolean myExecuteCommandInShell = true;
  private boolean mySudo = false;
  private String myRemoteWorkingDirectory;
  private boolean myPassEnvVarsUsingInterop = false;
  private final List<String> myInitShellCommands = new ArrayList<>();

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
   * Enables passing environment variables to WSL process using environmental variable interoperability between Win32/WSL.
   * See https://devblogs.microsoft.com/commandline/share-environment-vars-between-wsl-and-windows/ for details.
   * @param passEnvVarsUsingInterop true to pass environment variables using interoperability
   */
  public @NotNull WSLCommandLineOptions setPassEnvVarsUsingInterop(boolean passEnvVarsUsingInterop) {
    myPassEnvVarsUsingInterop = passEnvVarsUsingInterop;
    return this;
  }

  public @NotNull List<String> getInitShellCommands() {
    return myInitShellCommands;
  }

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

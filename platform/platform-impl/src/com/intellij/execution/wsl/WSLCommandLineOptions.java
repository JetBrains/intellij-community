// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WSLCommandLineOptions {

  private boolean myLaunchWithWslExe = true;
  private boolean myExecuteCommandInShell = true;
  private boolean mySudo = false;
  private String myRemoteWorkingDirectory;

  public boolean isLaunchWithWslExe() {
    return myLaunchWithWslExe;
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
    return this;
  }

  @Override
  public String toString() {
    return "launchWithWslExe=" + myLaunchWithWslExe +
           ", executeCommandInShell=" + myExecuteCommandInShell +
           ", sudo=" + mySudo +
           ", remoteWorkingDirectory='" + myRemoteWorkingDirectory + '\'';
  }
}

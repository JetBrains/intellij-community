/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.process;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class ProcessInfo {
  public static ProcessInfo[] EMPTY_ARRAY = new ProcessInfo[0];

  private final int myPid;
  @NotNull private final String myExecutable;
  @NotNull private final String myCommandLine;

  public ProcessInfo(@NotNull String pidString, @NotNull String commandLine) {
    this(Integer.parseInt(pidString), commandLine);
  }

  public ProcessInfo(int pid, @NotNull String commandLine) {
    myPid = pid;
    String[] args = commandLine.split(" ");
    myExecutable = args.length > 0 ? args[0] : "";
    myCommandLine = commandLine;
  }

  public ProcessInfo(int pid, @NotNull String executables, @NotNull String args) {
    myPid = pid;
    myExecutable = executables;
    myCommandLine = executables + " " + args;
  }

  public int getPid() {
    return myPid;
  }

  @NotNull
  public String getExecutable() {
    return myExecutable;
  }

  @NotNull
  public String getExecutableName() {
    return PathUtil.getFileName(myExecutable);
  }

  @NotNull
  public String getExecutableDisplayName() {
    return StringUtil.trimEnd(getExecutableName(), ".exe", true);
  }

  @NotNull
  public String getCommandLine() {
    return myCommandLine;
  }

  @Override
  public String toString() {
    return String.valueOf(myPid) + " (" + myCommandLine + ")";
  }
}
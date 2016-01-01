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
import org.jetbrains.annotations.Nullable;


public class ProcessInfo {
  public static ProcessInfo[] EMPTY_ARRAY = new ProcessInfo[0];

  private final int myPid;
  @NotNull private final String myExecutable;
  @NotNull private final String myArgs;
  @NotNull private final String myCommandLine;
  @Nullable private final String myUser;
  @Nullable private final String myState;

  public ProcessInfo(@NotNull String pidString, @NotNull String commandLine) {
    this(Integer.parseInt(pidString), commandLine);
  }

  public ProcessInfo(int pid, @NotNull String commandLine) {
    myPid = pid;
    int space = commandLine.indexOf(" ");
    if (space > 0) {
      myExecutable = commandLine.substring(0, space);
      myArgs = commandLine.substring(space + 1);
    }
    else {
      myExecutable = commandLine;
      myArgs = "";
    }
    
    myCommandLine = commandLine;
    myUser = null;
    myState = null;
  }

  public ProcessInfo(int pid, @NotNull String executable, @NotNull String args) {
    this(pid, executable, args, null, null);
  }

  public ProcessInfo(int pid, @NotNull String executable, @NotNull String args, @Nullable String user, @Nullable String state) {
    myPid = pid;
    myExecutable = executable;
    myArgs = args;
    myCommandLine = executable + (args.isEmpty() ? "" : (" " + args));
    myState = state;
    myUser = user;
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
  public String getArgs() {
    return myArgs;
  }

  @NotNull
  public String getCommandLine() {
    return myCommandLine;
  }

  @Nullable
  public String getUser() {
    return myUser;
  }

  @Nullable
  public String getState() {
    return myState;
  }

  @Override
  public String toString() {
    return myPid + " '" + myExecutable + "' '" + myArgs + "' '" + myUser + "' '" + myState;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProcessInfo info = (ProcessInfo)o;

    if (myPid != info.myPid) return false;
    if (!myExecutable.equals(info.myExecutable)) return false;
    if (!myArgs.equals(info.myArgs)) return false;
    if (!myCommandLine.equals(info.myCommandLine)) return false;
    if (myUser != null ? !myUser.equals(info.myUser) : info.myUser != null) return false;
    if (myState != null ? !myState.equals(info.myState) : info.myState != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPid;
    result = 31 * result + myExecutable.hashCode();
    result = 31 * result + myArgs.hashCode();
    result = 31 * result + myCommandLine.hashCode();
    result = 31 * result + (myUser != null ? myUser.hashCode() : 0);
    result = 31 * result + (myState != null ? myState.hashCode() : 0);
    return result;
  }
}
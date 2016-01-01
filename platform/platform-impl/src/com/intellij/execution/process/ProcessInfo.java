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
  @NotNull private final String myExecutableName;
  @NotNull private final String myArgs;
  @Nullable private final String myUser;
  @Nullable private final String myState;

  public ProcessInfo(int pid, @NotNull String executable) {
    this(pid, executable, "");
  }

  public ProcessInfo(int pid, @NotNull String executable, @NotNull String args) {
    this(pid, executable, args, null, null);
  }

  public ProcessInfo(int pid, @NotNull String executable, @NotNull String args, @Nullable String user, @Nullable String state) {
    myPid = pid;
    myExecutableName = PathUtil.getFileName(executable);
    myArgs = args;
    myState = state;
    myUser = user;
  }

  public int getPid() {
    return myPid;
  }

  @NotNull
  public String getExecutableName() {
    return myExecutableName;
  }

  @NotNull
  public String getExecutableDisplayName() {
    return StringUtil.trimEnd(myExecutableName, ".exe", true);
  }

  @NotNull
  public String getArgs() {
    return myArgs;
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
    return myPid + " '" + myExecutableName + "' '" + myArgs + "' '" + myUser + "' '" + myState;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProcessInfo info = (ProcessInfo)o;

    if (myPid != info.myPid) return false;
    if (!myExecutableName.equals(info.myExecutableName)) return false;
    if (!myArgs.equals(info.myArgs)) return false;
    if (myUser != null ? !myUser.equals(info.myUser) : info.myUser != null) return false;
    if (myState != null ? !myState.equals(info.myState) : info.myState != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPid;
    result = 31 * result + myExecutableName.hashCode();
    result = 31 * result + myArgs.hashCode();
    result = 31 * result + (myUser != null ? myUser.hashCode() : 0);
    result = 31 * result + (myState != null ? myState.hashCode() : 0);
    return result;
  }
}
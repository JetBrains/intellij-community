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
import org.jetbrains.annotations.NotNull;


public class ProcessInfo {
  public static ProcessInfo[] EMPTY_ARRAY = new ProcessInfo[0];

  private final int myPid;
  @NotNull private final String myCommandLine;
  @NotNull private final String myExecutableName;
  @NotNull private final String myArgs;

  public ProcessInfo(int pid,
                     @NotNull String commandLine,
                     @NotNull String executableName,
                     @NotNull String args) {
    myPid = pid;
    myCommandLine = commandLine;
    myExecutableName = executableName;
    myArgs = args;
  }

  public int getPid() {
    return myPid;
  }

  @NotNull
  public String getCommandLine() {
    return myCommandLine;
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

  @Override
  public String toString() {
    return myPid + " '" + myCommandLine + "' '" + myExecutableName + "' '" + myArgs + "'";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProcessInfo info = (ProcessInfo)o;

    if (myPid != info.myPid) return false;
    if (!myExecutableName.equals(info.myExecutableName)) return false;
    if (!myArgs.equals(info.myArgs)) return false;
    if (!myCommandLine.equals(info.myCommandLine)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPid;
    result = 31 * result + myExecutableName.hashCode();
    result = 31 * result + myArgs.hashCode();
    result = 31 * result + myCommandLine.hashCode();
    return result;
  }
}
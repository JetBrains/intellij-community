// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessManager;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.execution.process.impl.ProcessListUtil.getProcessList;

/**
 * @author nik
 */
public class OSProcessManagerImpl extends OSProcessManager {
  @Override
  public boolean killProcessTree(@NotNull Process process) {
    return OSProcessUtil.killProcessTree(process);
  }

  @Override
  @NotNull
  public List<String> getCommandLinesOfRunningProcesses() {
    return Arrays.stream(getProcessList()).map(ProcessInfo::getCommandLine).collect(Collectors.toList());
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessManager;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class OSProcessManagerImpl extends OSProcessManager {
  @Override
  @NotNull
  public List<String> getCommandLinesOfRunningProcesses() {
    return ContainerUtil.map(ProcessListUtil.getProcessList(), ProcessInfo::getCommandLine);
  }
}
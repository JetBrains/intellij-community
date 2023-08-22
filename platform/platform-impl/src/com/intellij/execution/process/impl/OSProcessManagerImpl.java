// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessManager;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class OSProcessManagerImpl extends OSProcessManager {
  @Override
  public @NotNull List<String> getCommandLinesOfRunningProcesses() {
    return ContainerUtil.map(ProcessListUtil.getProcessList(), ProcessInfo::getCommandLine);
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.MemTester;
import com.intellij.util.SystemProperties;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class MemTestAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    try {
      //A simple set of options for now: running memtester with 1/3 of physical memory
      // redirecting  the output to memtester_$ideapid.log in user home folder
      OperatingSystemMXBean bean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
      long totalPhysMemorySize = bean.getTotalPhysicalMemorySize();
      long totalPhysMemorySizeToTestInMb = totalPhysMemorySize / 3 / (1024 * 1024);
      String testingSizeArgument = totalPhysMemorySizeToTestInMb + "M";
      String pathToSaveLog = SystemProperties.getUserHome() + "/memtester_" + OSProcessUtil.getApplicationPid() + ".log";
      MemTester.scheduleMemTester(testingSizeArgument, "1", pathToSaveLog);
    }
    catch (IOException ex) {
      Logger.getInstance(MemTestAction.class).info("MemTester failed to launch");
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(MemTester.isSupported());
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class InspectionStatisticsCollector {
  private static final String GROUP_ID = "inspection.reports";

  private static final Cache<Pair<String, String>, Boolean> ourAlreadyReportedStats = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

  public static void trigger(@NotNull PsiFile file, @NotNull LocalInspectionToolWrapper tool, int problemsCount) {
    if (!PluginInfoDetectorKt.getPluginInfo(tool.getClass()).isDevelopedByJetBrains()) return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    String filePath = virtualFile.getPath();
    String toolName = tool.getShortName();
    Boolean alreadyReported = ourAlreadyReportedStats.asMap().putIfAbsent(Pair.create(filePath, toolName), Boolean.TRUE);
    if (Boolean.TRUE.equals(alreadyReported)) return;

    FUCounterUsageLogger instance = FUCounterUsageLogger.getInstance();
    Project project = file.getProject();
    for (int i = 0; i < problemsCount; i++) {
      instance.logEvent(project, GROUP_ID, toolName);
    }
  }
}

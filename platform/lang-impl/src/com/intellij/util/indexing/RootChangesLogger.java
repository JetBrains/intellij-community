// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.ExceptionUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class RootChangesLogger {
  private static final int BATCH_CAPACITY = 10;
  private static final @NotNull Logger myLogger = Logger.getInstance(RootChangesLogger.class);

  private final @NotNull IntOpenHashSet myReportedHashes = new IntOpenHashSet();
  private final List<Report> myReports = new ArrayList<>(BATCH_CAPACITY);

  void info(@NotNull Project project, boolean fullReindex) {
    if (!fullReindex) {
      if (myLogger.isTraceEnabled()) {
        myLogger.trace("New rootsChanged event for \"" + project.getName() + "\" project with " +
                       "partial rescanning:\n" + ExceptionUtil.getThrowableText(new Throwable()));
      }
      return;
    }
    Throwable stacktrace = new Throwable();
    int hash = ThrowableInterner.computeAccurateTraceHashCode(stacktrace);
    boolean isNew;
    synchronized (myReportedHashes) {
      isNew = myReportedHashes.add(hash);
    }

    if (isNew) {
      myLogger.info("New rootsChanged event for \"" + project.getName() + "\" project with " +
                    "full rescanning with trace_hash = " + hash + ":\n" + ExceptionUtil.getThrowableText(stacktrace));
      return;
    }

    Report[] reports = null;
    synchronized (myReports) {
      myReports.add(new Report(hash));
      if (myReports.size() == BATCH_CAPACITY) {
        reports = myReports.toArray(new Report[0]);
        myReports.clear();
      }
    }

    if (reports != null) {
      StringBuilder text = new StringBuilder();
      text.append(BATCH_CAPACITY).append(" more rootsChanged events for \"").append(project.getName()).append("\" project.");
      Arrays.stream(reports).collect(Collectors.groupingBy(report -> report)).forEach((report, equalHashes) -> {
        text.append(" ").append(equalHashes.size()).append(" full reindex with trace_hash = ").append(report.hash).append(";");
      });
      myLogger.info(text.substring(0, text.length() - 1));
    }
  }

  private record Report(int hash) {
  }
}

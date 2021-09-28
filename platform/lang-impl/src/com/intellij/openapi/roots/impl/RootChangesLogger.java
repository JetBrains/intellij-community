// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.util.ExceptionUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class RootChangesLogger {
  private final static int BATCH_CAPACITY = 10;
  @NotNull
  private final Logger myLogger;
  @NotNull
  private final IntOpenHashSet myReportedHashes = new IntOpenHashSet();
  private final List<Report> myReports = new ArrayList<>(BATCH_CAPACITY);

  RootChangesLogger(@NotNull Logger logger) { myLogger = logger; }

  /**
   * Actually if it's full reindex is evident from stacktrace.
   * Explicit parameter is to simplify reading old builds' logs only
   * <p>
   * Also it's the same project for all invocations
   */
  void info(@NotNull Project project, boolean fullReindex) {
    Report[] reports = null;
    synchronized (myReports) {
      myReports.add(new Report(fullReindex));
      if (myReports.size() == BATCH_CAPACITY) {
        reports = myReports.toArray(new Report[0]);
        myReports.clear();
      }
    }

    if (reports != null) {
      StringBuilder text = new StringBuilder();
      text.append(BATCH_CAPACITY).append(" more rootsChanged events for \"").append(project.getName()).append("\" project.");
      List<Integer> hashes = new ArrayList<>(BATCH_CAPACITY);
      boolean wereAdded = false;
      for (Report report : reports) {
        int hash = ThrowableInterner.computeAccurateTraceHashCode(report.stacktrace);
        boolean added;
        synchronized (myReportedHashes) {
          added = myReportedHashes.add(hash);
        }
        if (added) {
          wereAdded = true;
          text.append("\nNew ").append(report.myFullReindex ? "full" : "partial").append(" reindex with trace_hash = ").append(hash)
            .append(":\n").append(ExceptionUtil.getThrowableText(report.stacktrace));
        }
        else {
          hashes.add(hash);
        }
      }
      boolean hasHashes = !hashes.isEmpty();
      if (hasHashes) {
        text.append(" ");
        if (wereAdded) {
          text.append("\n");
        }
        hashes.stream().collect(Collectors.groupingBy(hash -> hash)).forEach((hash, equalHashes) -> {
          text.append(equalHashes.size()).append(" with trace_hash = ").append(hash).append(";");
        });
      }
      myLogger.info(hasHashes ? text.substring(0, text.length() - 1) : text.toString());
    }
  }

  private static class Report {
    private final Throwable stacktrace = new Throwable();
    private final boolean myFullReindex;

    Report(boolean isFullReindex) {
      myFullReindex = isFullReindex;
    }
  }
}

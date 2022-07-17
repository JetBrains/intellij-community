// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.Objects;
import java.util.stream.Collectors;

final class RootChangesLogger {
  private final static int BATCH_CAPACITY = 10;
  @NotNull
  private static final Logger myLogger = Logger.getInstance(RootChangesLogger.class);

  @NotNull
  private final IntOpenHashSet myReportedHashes = new IntOpenHashSet();
  private final List<Report> myReports = new ArrayList<>(BATCH_CAPACITY);

  void info(@NotNull Project project, boolean fullReindex) {
    Throwable stacktrace = new Throwable();
    int hash = ThrowableInterner.computeAccurateTraceHashCode(stacktrace);
    boolean isNew;
    synchronized (myReportedHashes) {
      isNew = myReportedHashes.add(hash);
    }

    if (isNew) {
      myLogger.info("New rootsChanged event for \"" + project.getName() + "\" project with " +
                    (fullReindex ? "full" : "partial") + " rescanning with trace_hash = " + hash + ":\n" +
                    ExceptionUtil.getThrowableText(stacktrace));
      return;
    }

    Report[] reports = null;
    synchronized (myReports) {
      myReports.add(new Report(hash, fullReindex));
      if (myReports.size() == BATCH_CAPACITY) {
        reports = myReports.toArray(new Report[0]);
        myReports.clear();
      }
    }

    if (reports != null) {
      StringBuilder text = new StringBuilder();
      text.append(BATCH_CAPACITY).append(" more rootsChanged events for \"").append(project.getName()).append("\" project.");
      Arrays.stream(reports).collect(Collectors.groupingBy(report -> report)).forEach((report, equalHashes) -> {
        text.append(" ").append(equalHashes.size()).append(" ").append(report.myFullReindex ? "full" : "partial").
          append(" reindex with trace_hash = ").append(report.myHash).append(";");
      });
      myLogger.info(text.substring(0, text.length() - 1));
    }
  }

  private static class Report {
    private final int myHash;
    private final boolean myFullReindex;

    Report(int hash, boolean isFullReindex) {
      myHash = hash;
      myFullReindex = isFullReindex;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Report report = (Report)o;
      return myHash == report.myHash && myFullReindex == report.myFullReindex;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myHash, myFullReindex);
    }
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentSkipListMap;

public final class PerformanceTrace implements Serializable {

  private static final Logger LOG = Logger.getInstance(PerformanceTrace.class);

  public static final Key<PerformanceTrace> TRACE_NODE_KEY = Key.create(PerformanceTrace.class, ExternalSystemConstants.UNORDERED + 1);

  private final Map<String, Long> performanceData = new ConcurrentSkipListMap<>();

  private final long myId;

  public PerformanceTrace() {
    this(0);
  }

  public PerformanceTrace(long id) {
    myId = id;
  }

  public long getId() {
    return myId;
  }

  public void logPerformance(@NotNull String key, long millis) {
    performanceData.put(key, millis);
  }

  @NotNull
  public Map<String, Long> getPerformanceTrace() {
    return performanceData;
  }

  public void addTrace(@NotNull Map<String, Long> trace) {
    performanceData.putAll(trace);
  }

  public void reportStatistics() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Gradle successful import performance trace");
      for (var entry : getPerformanceTrace().entrySet()) {
        LOG.debug("%s : %d ms.".formatted(entry.getKey(), entry.getValue()));
      }
    }
  }

  /**
   * Stores performance traces into sequent files in specified directory.
   * One file for one Gradle reload performance trace.
   */
  @SuppressWarnings("unused")
  public void reportStatisticsToFile(@NotNull Path directory) {
    var traceJoiner = new StringJoiner("\n");
    for (var entry : getPerformanceTrace().entrySet()) {
      var description = entry.getKey();
      var duration = entry.getValue();
      var apply = "%s : %d ms.".formatted(description, duration);
      traceJoiner.add(apply);
    }
    try {
      var file = FileUtil.findSequentNonexistentFile(directory.toFile(), "performance-trace", "txt");
      Files.createDirectories(directory);
      Files.writeString(file.toPath(), traceJoiner.toString());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

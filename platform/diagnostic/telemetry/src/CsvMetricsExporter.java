// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry;

import com.intellij.openapi.diagnostic.Logger;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

/**
 * Export {@linkplain MetricData} into a file in a simple CSV format:
 * name, epochStartNanos, epochEndNanos, value
 * <br/>
 * <br/>
 * This is expected to be temporary solution for metrics export -- until full-fledged (json?) exporter will be implemented.
 * That is why implementation is quite limited: only simplest metrics types are supported (e.g. no support for histograms),
 * no support for attributes, and IO/file format itself is not the most effective one. But until now it seems like this limited
 * implementation could be enough at least for a while.
 * <p>
 * <br/>
 * <p>
 * TODO not all metrics types are supported now, see .toCSVLine()
 * TODO no support for attributes now, see .toCSVLine()
 */
@ApiStatus.Internal
public final class CsvMetricsExporter implements MetricExporter {
  private static final Logger LOGGER = Logger.getInstance(CsvMetricsExporter.class);

  private final @NotNull Path writeToFile;


  public CsvMetricsExporter(final @NotNull Path writeToFile) throws IOException {
    this.writeToFile = writeToFile;

    if (!Files.exists(writeToFile)) {
      final Path parentDir = writeToFile.getParent();
      if (!Files.isDirectory(parentDir)) {
        //RC: createDirectories() _does_ throw FileAlreadyExistsException if path is a _symlink_ to a directory, not a directory
        // itself (JDK-8130464). Check !isDirectory() above should work around that case.
        Files.createDirectories(parentDir);
      }
    }
    if (!Files.exists(writeToFile) || Files.size(writeToFile) == 0) {
      Files.write(writeToFile, csvHeadersLines(), CREATE, WRITE);
    }
  }

  @Override
  public AggregationTemporality getAggregationTemporality(final @NotNull InstrumentType instrumentType) {
    return AggregationTemporality.DELTA;
  }

  @Override
  public CompletableResultCode export(final Collection<MetricData> metrics) {
    if (metrics.isEmpty()) {
      return CompletableResultCode.ofSuccess();
    }

    final CompletableResultCode result = new CompletableResultCode();
    final List<String> lines = metrics.stream()
      .flatMap(CsvMetricsExporter::toCSVLine)
      .toList();

    try {
      Files.write(writeToFile, lines, CREATE, APPEND);
      result.succeed();
    }
    catch (IOException e) {
      LOGGER.warn("Can't write metrics into " + writeToFile.toAbsolutePath(), e);
      result.fail();
    }
    return result;
  }

  private static Stream<String> toCSVLine(final MetricData metricData) {
    return switch (metricData.getType()) {
      case LONG_SUM -> metricData.getLongSumData().getPoints().stream().map(
        p ->
          concatToCsvLine(metricData.getName(), p.getStartEpochNanos(), p.getEpochNanos(), String.valueOf(p.getValue()))
      );
      case DOUBLE_SUM -> metricData.getDoubleSumData().getPoints().stream().map(
        p ->
          concatToCsvLine(metricData.getName(), p.getStartEpochNanos(), p.getEpochNanos(), String.valueOf(p.getValue()))
      );
      case LONG_GAUGE -> metricData.getLongGaugeData().getPoints().stream().map(
        p ->
          concatToCsvLine(metricData.getName(), p.getStartEpochNanos(), p.getEpochNanos(), String.valueOf(p.getValue()))
      );
      case DOUBLE_GAUGE -> metricData.getDoubleGaugeData().getPoints().stream().map(
        p ->
          concatToCsvLine(metricData.getName(), p.getStartEpochNanos(), p.getEpochNanos(), String.valueOf(p.getValue()))
      );
      default -> Stream.of(
        concatToCsvLine(metricData.getName(), -1, -1, "<metrics type " + metricData.getType() + " is not supported yet>")
      );
    };
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @NotNull
  private static List<String> csvHeadersLines() {
    return List.of(
      "# OpenTelemetry Metrics report: .csv, 4 fields (metric name, period start-end nanoseconds, metric value)" +
      "# See CsvMetricsExporter for details",
      "name, startEpochNanos, endEpochNanos, value"
    );
  }

  @NotNull
  private static String concatToCsvLine(final String name,
                                        final long startEpochNanos,
                                        final long endEpochNanos,
                                        final String value) {
    return name + ',' + startEpochNanos + ',' + endEpochNanos + ',' + value;
  }
}

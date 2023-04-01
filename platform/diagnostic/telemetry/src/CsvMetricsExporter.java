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
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
  private static final Logger LOG = Logger.getInstance(CsvMetricsExporter.class);

  private static final String HTML_PLOTTER_NAME = "open-telemetry-metrics-plotter.html";


  private final @NotNull RollingFileSupplier writeToFileSupplier;

  public CsvMetricsExporter(final @NotNull RollingFileSupplier writeToFileSupplier) throws IOException {
    this.writeToFileSupplier = writeToFileSupplier;
    final Path writeToFile = writeToFileSupplier.get();

    if (!Files.exists(writeToFile)) {
      final Path parentDir = writeToFile.getParent();
      if (!Files.isDirectory(parentDir)) {
        //RC: createDirectories() _does_ throw FileAlreadyExistsException if path is a _symlink_ to a directory, not a directory
        // itself (JDK-8130464). Check !isDirectory() above should work around that case.
        Files.createDirectories(parentDir);
      }
    }
    if (!Files.exists(writeToFile) || Files.size(writeToFile) == 0) {
      Files.write(writeToFile, OpenTelemetryUtils.csvHeadersLines(), CREATE, WRITE);
    }

    copyHtmlPlotterToOutputDir(writeToFile.getParent());
  }

  /** Copy html file with plotting scripts into targetDir */
  private static void copyHtmlPlotterToOutputDir(final @NotNull Path targetDir) throws IOException {
    final Path targetToCopyTo = targetDir.resolve(HTML_PLOTTER_NAME);
    final URL plotterHtmlUrl = CsvMetricsExporter.class.getResource(HTML_PLOTTER_NAME);
    if (plotterHtmlUrl == null) {
      LOG.info(HTML_PLOTTER_NAME + " is not found in classpath");
    }
    else {
      try (InputStream stream = plotterHtmlUrl.openStream()) {
        final byte[] bytes = stream.readAllBytes();
        Files.write(targetToCopyTo, bytes);
      }
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
    final Path writeToFile = writeToFileSupplier.get();
    final List<String> lines = metrics.stream()
      .flatMap(OpenTelemetryUtils::toCsvStream)
      .collect(Collectors.toList());

    try {
      Files.write(writeToFile, lines, CREATE, APPEND);
      result.succeed();
    }
    catch (IOException e) {
      LOG.warn("Can't write metrics into " + writeToFile.toAbsolutePath(), e);
      result.fail();
    }
    return result;
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.agent.extension;

import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.System.currentTimeMillis;

/**
 * Implementation of the {@link SpanExporter} that export collected spans as JSON objects into the file path provided by a
 * {@link ConfigProperties}.
 * The implementation IS _NOT_ thread safe and shouldn't be used with a {@link io.opentelemetry.sdk.trace.SpanProcessor} different from
 * {@link io.opentelemetry.sdk.trace.export.BatchSpanProcessor}.
 * Only {@link io.opentelemetry.sdk.trace.export.BatchSpanProcessor} guarantees thread-safe {@link SpanExporter} invocations.
 */
class JsonSpanExporter implements SpanExporter {

  private static final Logger LOG = Logger.getLogger(JsonSpanExporter.class.getName());

  private final @NotNull Path targetFile;

  private JsonSpanExporter(@NotNull ConfigProperties config) {
    this.targetFile = getTargetFile(config);
  }

  @Override
  public CompletableResultCode export(@NotNull Collection<SpanData> spans) {
    return write(targetFile, stream -> {
      TraceRequestMarshaler.create(spans)
        .writeJsonTo(stream);
    });
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  private static @NotNull CompletableResultCode write(@NotNull Path targetFile, @NotNull BufferOperation<OutputStream> consumer) {
    try {
      String json = captureIntoBuffer(consumer);
      appendJsonToFile(targetFile, json);
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, String.format("Unable to flush an opentelemetry dump into %s: %s", targetFile, e.getMessage()));
      return CompletableResultCode.ofFailure();
    }
    return CompletableResultCode.ofSuccess();
  }

  public static @NotNull SpanProcessor createProcessor(@NotNull ConfigProperties config) {
    JsonSpanExporter exporter = new JsonSpanExporter(config);
    SpanProcessor processor = BatchSpanProcessor.builder(exporter)
      .setExporterTimeout(Duration.ofMinutes(1))
      .setMaxQueueSize(1024 * 4)
      .setMaxExportBatchSize(1024 * 2)
      .build();
    return processor;
  }

  private static @NotNull String captureIntoBuffer(@NotNull BufferOperation<OutputStream> consumer) throws Exception {
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
      consumer.execute(buffer);
      return buffer.toString(StandardCharsets.UTF_8.name());
    }
  }

  private static void appendJsonToFile(@NotNull Path pathToFile, @NotNull String json) throws IOException {
    if (!Files.exists(pathToFile)) {
      ensureParentExists(pathToFile);
      Files.write(pathToFile, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      LOG.info(String.format("Telemetry will be written into %s", pathToFile));
      return;
    }
    String content = JsonUtil.merge(
      String.join(" ", Files.readAllLines(pathToFile)),
      json
    );
    Path temp = pathToFile.getParent().resolve("temp_" + currentTimeMillis() + ".json");
    try {
      Files.write(temp, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      Files.move(temp, pathToFile, StandardCopyOption.REPLACE_EXISTING);
    }
    finally {
      Files.deleteIfExists(temp);
    }
  }

  private static void ensureParentExists(@NotNull Path path) throws IOException {
    Path parent = path.getParent();
    if (Files.exists(parent)) {
      return;
    }
    Files.createDirectories(parent);
  }

  private static @NotNull Path getTargetFile(@NotNull ConfigProperties config) {
    Path root = Configuration.getTargetJsonLocation(config);
    String serviceName = Configuration.getServiceName(config);
    String outputFileName = serviceName + "-telemetry-" + currentTimeMillis() + ".json";
    return root.resolve(outputFileName);
  }

  @FunctionalInterface
  private interface BufferOperation<T> {
    void execute(T t) throws IOException;
  }
}

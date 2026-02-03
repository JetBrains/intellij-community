// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.agent.extension;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

@ApiStatus.Internal
public class TelemetryAgentCustomizerProvider implements AutoConfigurationCustomizerProvider {

  private static final Logger LOG = Logger.getLogger(TelemetryAgentCustomizerProvider.class.getName());

  @Override
  public void customize(@NotNull AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addTracerProviderCustomizer(TelemetryAgentCustomizerProvider::configureSdkTracerProvider);
    LOG.info("TelemetryAgentCustomizerProvider was applied successfully");
  }

  private static @NotNull SdkTracerProviderBuilder configureSdkTracerProvider(
    @NotNull SdkTracerProviderBuilder tracerProvider,
    @NotNull ConfigProperties config
  ) {
    if (Configuration.isJsonExporterEnabled(config)) {
      SpanProcessor processor = JsonSpanExporter.createProcessor(config);
      tracerProvider.addSpanProcessor(processor);
      LOG.info("JSON file exporter enabled");
    }
    return tracerProvider;
  }
}

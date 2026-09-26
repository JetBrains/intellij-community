// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.agent.extension;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

final class Configuration {

  private static final String DEFAULT_SERVICE_NAME = "unknown";

  private static final String SERVICE_NAME_PROPERTY = "otel.service.name";
  private static final String JSON_EXPORTER_CONFIGURATION_PROPERTY = "otel.traces.exporter.json.file";
  private static final String EXPORTER_ENABLED_PROPERTY = JSON_EXPORTER_CONFIGURATION_PROPERTY + ".enabled";
  private static final String EXPORT_LOCATION_PROPERTY = JSON_EXPORTER_CONFIGURATION_PROPERTY + ".location";

  public static boolean isJsonExporterEnabled(@NotNull ConfigProperties config) {
    try {
      return Boolean.TRUE.equals(config.getBoolean(EXPORTER_ENABLED_PROPERTY));
    }
    catch (Exception e) {
      return false;
    }
  }

  public static @NotNull String getServiceName(@NotNull ConfigProperties properties) {
    try {
      String serviceName = properties.getString(SERVICE_NAME_PROPERTY);
      if (serviceName == null || serviceName.trim().isEmpty()) {
        return DEFAULT_SERVICE_NAME;
      }
      return serviceName;
    }
    catch (Exception e) {
      return DEFAULT_SERVICE_NAME;
    }
  }

  public static @NotNull Path getTargetJsonLocation(@NotNull ConfigProperties config) {
    String location = config.getString(EXPORT_LOCATION_PROPERTY);
    if (location == null) {
      throw new IllegalStateException("The property " + EXPORT_LOCATION_PROPERTY + " should not be null!");
    }
    return Paths.get(location);
  }
}

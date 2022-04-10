// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesSpan
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTraceEventAttributes
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTracer

@CompileStatic
class BuildDependenciesOpenTelemetryTracer implements BuildDependenciesTracer {
  static final BuildDependenciesTracer INSTANCE = new BuildDependenciesOpenTelemetryTracer()

  private BuildDependenciesOpenTelemetryTracer() {
  }

  @Override
  BuildDependenciesTraceEventAttributes createAttributes() {
    return new BuildDependenciesOpenTelemetryAttributes()
  }

  @Override
  BuildDependenciesSpan startSpan(@NotNull String name, @NotNull BuildDependenciesTraceEventAttributes attributes) {
    return new BuildDependenciesOpenTelemetrySpan(name, attributes)
  }

  private static class BuildDependenciesOpenTelemetrySpan implements BuildDependenciesSpan {
    private final Span mySpan

    BuildDependenciesOpenTelemetrySpan(@NotNull String name, @NotNull BuildDependenciesTraceEventAttributes attributes) {
      def spanBuilder = TracerManager.spanBuilder(name)
      spanBuilder.setAllAttributes(((BuildDependenciesOpenTelemetryAttributes)attributes).attributes)
      mySpan = spanBuilder.startSpan()
    }

    @Override
    void addEvent(@NotNull String name, @NotNull BuildDependenciesTraceEventAttributes attributes) {
      mySpan.addEvent(name, ((BuildDependenciesOpenTelemetryAttributes)attributes).attributes)
    }

    @Override
    void recordException(@NotNull Throwable throwable) {
      mySpan.recordException(throwable)
    }

    @SuppressWarnings('UnnecessaryQualifiedReference')
    @Override
    void setStatus(@NotNull BuildDependenciesSpan.SpanStatus status) {
      StatusCode statusCode
      switch (status) {
        case SpanStatus.UNSET:
          statusCode = StatusCode.UNSET
          break
        case SpanStatus.OK:
          statusCode = StatusCode.OK
          break
        case SpanStatus.ERROR:
          statusCode = StatusCode.ERROR
          break
        default:
          throw new IllegalArgumentException("Unsupported span status: " + status)
      }

      mySpan.setStatus(statusCode)
    }

    @Override
    void close() throws IOException {
      mySpan.end()
    }
  }

  private static class BuildDependenciesOpenTelemetryAttributes implements BuildDependenciesTraceEventAttributes {
    private AttributesBuilder builder = Attributes.builder()

    Attributes getAttributes() {
      return builder.build()
    }

    @Override
    void setAttribute(@NotNull String name, @NotNull String value) {
      builder.put(name, value)
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies.telemetry

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class BuildDependenciesNoopTracer implements BuildDependenciesTracer {
  static final BuildDependenciesNoopTracer INSTANCE = new BuildDependenciesNoopTracer()

  private BuildDependenciesNoopTracer() {
  }

  @Override
  BuildDependenciesTraceEventAttributes createAttributes() {
    return new BuildDependenciesTraceEventAttributes() {
      @Override
      void setAttribute(@NotNull String name, @NotNull String value) {
      }
    }
  }

  @Override
  BuildDependenciesSpan startSpan(@NotNull String name, @NotNull BuildDependenciesTraceEventAttributes attributes) {
    return new BuildDependenciesSpan() {
      @Override
      void addEvent(@NotNull String eventName, @NotNull BuildDependenciesTraceEventAttributes eventAttributes) {
      }

      @Override
      void recordException(@NotNull Throwable throwable) {
      }

      @Override
      void setStatus(@NotNull BuildDependenciesSpan.SpanStatus status) {
      }

      @Override
      void close() throws IOException {
      }
    }
  }
}

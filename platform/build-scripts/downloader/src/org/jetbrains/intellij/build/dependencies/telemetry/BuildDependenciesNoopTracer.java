// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies.telemetry;

import org.jetbrains.annotations.NotNull;

public class BuildDependenciesNoopTracer implements BuildDependenciesTracer {
  public static final BuildDependenciesNoopTracer INSTANCE = new BuildDependenciesNoopTracer();

  private BuildDependenciesNoopTracer() {
  }

  @Override
  public BuildDependenciesTraceEventAttributes createAttributes() {
    return new BuildDependenciesTraceEventAttributes() {
      @Override
      public void setAttribute(@NotNull String name, @NotNull String value) {
      }
    };
  }

  @Override
  public BuildDependenciesSpan startSpan(@NotNull String name, @NotNull BuildDependenciesTraceEventAttributes attributes) {
    return new BuildDependenciesSpan() {
      @Override
      public void addEvent(@NotNull String eventName, @NotNull BuildDependenciesTraceEventAttributes eventAttributes) {
      }

      @Override
      public void recordException(@NotNull Throwable throwable) {
      }

      @Override
      public void setStatus(@NotNull BuildDependenciesSpan.SpanStatus status) {
      }

      @Override
      public void close() {
      }
    };
  }
}

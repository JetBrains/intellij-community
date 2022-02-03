// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies.telemetry

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
interface BuildDependenciesSpan extends Closeable {
  void addEvent(@NotNull String name, @NotNull BuildDependenciesTraceEventAttributes attributes)
  void recordException(@NotNull Throwable throwable)
  void setStatus(@NotNull SpanStatus status)

  enum SpanStatus {
    UNSET, OK, ERROR,
  }
}

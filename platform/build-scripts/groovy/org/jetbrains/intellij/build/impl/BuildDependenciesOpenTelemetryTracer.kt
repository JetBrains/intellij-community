// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.intellij.build.TraceManager
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesSpan
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTraceEventAttributes
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTracer

class BuildDependenciesOpenTelemetryTracer private constructor() : BuildDependenciesTracer {
  companion object {
    @JvmField
    val INSTANCE: BuildDependenciesTracer = BuildDependenciesOpenTelemetryTracer()
  }

  override fun createAttributes(): BuildDependenciesTraceEventAttributes = BuildDependenciesOpenTelemetryAttributes()

  override fun startSpan(name: String, attributes: BuildDependenciesTraceEventAttributes): BuildDependenciesSpan {
    return BuildDependenciesOpenTelemetrySpan(name, attributes)
  }
}

private class BuildDependenciesOpenTelemetrySpan(name: String, attributes: BuildDependenciesTraceEventAttributes) : BuildDependenciesSpan {
  private val span: Span

  init {
    val spanBuilder = TraceManager.spanBuilder(name)
    spanBuilder.setAllAttributes((attributes as BuildDependenciesOpenTelemetryAttributes).getAttributes())
    span = spanBuilder.startSpan()
  }

  override fun addEvent(name: String, attributes: BuildDependenciesTraceEventAttributes) {
    span.addEvent(name, (attributes as BuildDependenciesOpenTelemetryAttributes).getAttributes())
  }

  override fun recordException(throwable: Throwable) {
    span.recordException(throwable)
  }

  override fun setStatus(status: BuildDependenciesSpan.SpanStatus) {
    val statusCode = when (status) {
      BuildDependenciesSpan.SpanStatus.UNSET -> StatusCode.UNSET
       BuildDependenciesSpan.SpanStatus.OK -> StatusCode.OK
      BuildDependenciesSpan.SpanStatus.ERROR -> StatusCode.ERROR
      else -> throw IllegalArgumentException("Unsupported span status: $status")
    }

    span.setStatus(statusCode)
  }

  override fun close() {
    span.end()
  }
}

private class BuildDependenciesOpenTelemetryAttributes : BuildDependenciesTraceEventAttributes {
  private val builder = Attributes.builder()

  fun getAttributes(): Attributes = builder.build()

  override fun setAttribute(name: String, value: String) {
    builder.put(name, value)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.UI
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

internal enum class FreezeNotSentReason(val attributeValue: String) {
  INTERRUPTED_BY_NEW_FREEZE("interrupted_by_new_freeze"),
  DEBUGGER_ATTACHED("debugger_attached"),
  SAMPLING_DISABLED("sampling_disabled"),
  REPORTER_DISABLED("reporter_disabled"),
  BELOW_DURATION_THRESHOLD("below_duration_threshold"),
  NO_COMMON_STACK("no_common_stack"),
  NOT_ENOUGH_DUMPS("not_enough_dumps"),
  EVENT_CREATION_FAILED("event_creation_failed"),
  AUTO_REPORT_DISABLED("auto_report_disabled"),
  NOT_AUTO_REPORTABLE("not_auto_reportable"),
  PROCESSING_FAILED("processing_failed"),
}

@ApiStatus.Internal
class FreezeReporterTelemetry private constructor(private val span: Span) {
  private val finished = AtomicBoolean()

  fun freezeDetected() {
    addEvent("freeze detected")
  }

  fun freezeQueued(durationMs: Long) {
    addEvent("freeze queued", durationMs = durationMs)
    setSpanAttributes(durationMs, reason = null)
  }

  fun freezeSent() {
    addEvent("freeze sent")
  }

  internal fun freezeNotSent(reason: FreezeNotSentReason, durationMs: Long? = null) {
    addEvent("freeze not sent", durationMs = durationMs, reason = reason)
    setSpanAttributes(durationMs, reason)
  }

  internal fun finishNotSent(reason: FreezeNotSentReason, durationMs: Long? = null) {
    freezeNotSent(reason, durationMs)
    finish()
  }

  fun finish() {
    if (finished.compareAndSet(false, true)) {
      span.end()
    }
  }

  private fun addEvent(eventName: String, durationMs: Long? = null, reason: FreezeNotSentReason? = null) {
    if (finished.get()) {
      return
    }
    span.addEvent(eventName, createAttributes(durationMs, reason))
  }

  private fun setSpanAttributes(durationMs: Long?, reason: FreezeNotSentReason?) {
    if (finished.get()) {
      return
    }
    if (durationMs != null) {
      span.setAttribute(DURATION_MS_ATTRIBUTE, durationMs)
    }
    if (reason != null) {
      span.setAttribute(NOT_SENT_REASON_ATTRIBUTE, reason.attributeValue)
    }
  }

  companion object {
    private val DURATION_MS_ATTRIBUTE: AttributeKey<Long> = AttributeKey.longKey("freeze.duration.ms")
    private val NOT_SENT_REASON_ATTRIBUTE: AttributeKey<String> = AttributeKey.stringKey("freeze.not_sent.reason")
    private val tracer = TelemetryManager.getTracer(UI)

    fun start(): FreezeReporterTelemetry {
      return FreezeReporterTelemetry(tracer.spanBuilder("freeze processing").startSpan())
    }

    fun startSending(): FreezeReporterTelemetry {
      return FreezeReporterTelemetry(tracer.spanBuilder("freeze auto-report sending").startSpan())
    }

    private fun createAttributes(durationMs: Long?, reason: FreezeNotSentReason?): Attributes {
      val builder = Attributes.builder()
      if (durationMs != null) {
        builder.put(DURATION_MS_ATTRIBUTE, durationMs)
      }
      if (reason != null) {
        builder.put(NOT_SENT_REASON_ATTRIBUTE, reason.attributeValue)
      }
      return builder.build()
    }
  }
}

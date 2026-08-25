// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")
package org.jetbrains.intellij.build.devServer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.telemetry.withTracer
import org.jetbrains.intellij.build.telemetry.withoutTracer
import java.nio.file.Path

/**
 * The option every dev-distribution producer takes to write the spans of its own process out, in the Jaeger JSON shape
 * [com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter] produces.
 *
 * A trace file is a pure side output: nothing reads it while the build runs, so a producer that is not given one must
 * behave exactly as it did before it could be.
 */
internal const val TRACE_FILE_OPTION: String = "--trace-file"

/**
 * Runs [block] as the whole of a producer's work, under a single root span named [jobName] when - and only when - a
 * [traceFile] was asked for.
 *
 * One root span per process is what makes the per-action trace files mergeable: a merged timeline nests every span of
 * an action under the one span that names what that action was for, and the action's own spans are the only ones in
 * its file. That is also why the root span is opened here rather than by whatever the producer calls - a producer that
 * opened two of them would be two unrelated traces in one file.
 *
 * With a [traceFile], [withTracer] owns the exporter lifecycle: its span processor runs in a scope that is a child of
 * the `runBlocking` job, so the trace file is written, closed and complete by the time this returns, before the process
 * reports success. It also pins the exporter set to the console and the trace file - unlike `TraceManager`'s default
 * initializer, which adds an OTLP exporter as soon as `OTLP_ENDPOINT` is set, and these actions run with no network.
 *
 * Without one, each producer keeps the tracer it had before it could write a trace file - see
 * [consoleSpansWhenNotMeasuring]. Either way no root span is opened, because a root span exists only to structure a
 * trace file and there is none to structure.
 *
 * @param consoleSpansWhenNotMeasuring `true` for a producer that already had a tracer, and so already printed a
 * console span dump, before this option existed - which is only [DevDistMain]. It then keeps `TraceManager`'s default
 * initializer. `false` installs no tracer at all: the spans are non-recording, so nothing is exported, nothing is
 * printed, and no exporter coroutine is started - which is what the three producers that never had a tracer did.
 *
 * The invariant is *unchanged when not measuring*, per producer, not *silent when not measuring*. Do not collapse the
 * two branches into the quiet one: for the fragment assembler the console dump is the only visibility a failing build
 * has today, and dropping it as a side effect of adding measurement is the opposite of the point. Making those actions
 * stop printing spans is a fine thing to decide, but it is its own change with its own justification.
 *
 * Nor should the silent branch start opening a root span: [use] goes through
 * `TeamCityBuildMessageLogger.withFlow`, which prints a `flowStarted` service message under TeamCity even for a
 * non-recording span - a noop span is not a `ReadableSpan`, so the branch that is skipped is the parent-flow
 * attribute, not the print.
 */
internal fun runDevDistJob(
  traceFile: Path?,
  jobName: String,
  consoleSpansWhenNotMeasuring: Boolean = false,
  block: suspend CoroutineScope.() -> Unit,
) {
  if (traceFile != null) {
    withTracer(serviceName = jobName, traceFile = traceFile) {
      spanBuilder(jobName).use { block() }
      // The root span has ended by now, and this is what puts it in the file. `withTracer` closes the file by
      // cancelling the exporter's scope, which is a shutdown path: it reports nothing, and until
      // `BatchSpanProcessor` learned to drain its queue there it dropped whatever had not reached the current batch
      // yet - which is exactly a span that ended last. Flushing here instead makes the file complete while the
      // processor is still running, before the process reports success, and leaves the cancellation nothing to do.
      TraceManager.flush()
    }
    return
  }

  runBlocking(Dispatchers.Default) {
    if (consoleSpansWhenNotMeasuring) {
      block()
    }
    else {
      withoutTracer {
        coroutineScope { block() }
      }
    }
  }
}

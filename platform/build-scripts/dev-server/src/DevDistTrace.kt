// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer

import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.telemetry.withTracer
import org.jetbrains.intellij.build.telemetry.withoutTracer
import java.nio.file.Path

/**
 * The option every dev-distribution producer takes to write the spans of its own process out, in the Jaeger JSON shape
 * [org.jetbrains.intellij.build.telemetry.TraceFileSpanExporter] produces.
 *
 * A trace file is a pure side output: nothing reads it while the build runs, so a producer that is not given one must
 * behave exactly as it did before it could be.
 *
 * Reuse metadata needed by adjacent file operations for span attributes. Gate extra work that serves only tracing.
 * The Go sourced collector counts source bytes during collection and inventory. The composer counts bytes beside
 * file copies. The jar packer collects extra file statistics only when tracing is enabled.
 *
 * The Kotlin manifest inventory needs file sizes for hashing, with or without tracing.
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
 * With a [traceFile], [withTracer] owns the exporter lifecycle: it closes its span processor when the block returns,
 * so the trace file is written, closed and complete by the time this returns, before the process reports success. It also pins the exporter set to the console and the trace file - unlike `TraceManager`'s default
 * initializer, which adds an OTLP exporter as soon as `OTLP_ENDPOINT` is set, and these actions run with no network.
 *
 * Without one, each producer keeps the tracer it had before it could write a trace file - see
 * [consoleSpansWhenNotMeasuring]. Either way no root span is opened, because a root span exists only to structure a
 * trace file and there is none to structure.
 *
 * @param consoleSpansWhenNotMeasuring `true` preserves `DevDistMain` console tracing through the default
 * `TraceManager` initializer when no trace file is requested. `false` disables tracing, console spans, and the exporter
 * thread in that case.
 *
 * What that branch preserves is *same outputs, same stdout, same exit code* - not "byte for byte what the main did
 * before". Wrapping the whole body widened the traced region of `DevDistMain` from `buildProductInProcess` to
 * everything around it, so `materializeProjectModelTree`, the `println` and `writeUnusedInputs` now run inside the
 * root span as well. Nothing there is
 * thread-affine, which is why it is fine; the stronger claim is not true and should not be repeated.
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
  block: () -> Unit,
) {
  if (traceFile != null) {
    withTracer(serviceName = jobName, traceFile = traceFile) {
      spanBuilder(jobName).use { block() }
      // The root span has ended by now, and this is what puts it in the file. `withTracer` closes the file through the
      // shutdown of its span processor, and a shutdown reports nothing. Flushing here instead makes the file complete
      // while the processor is still running, before the process reports success, and leaves the shutdown nothing to do.
      //
      // This flushes the *right* processor only because nothing has touched `TraceManager` before now. That object
      // runs `traceManagerInitializer` once, at first access, and `withTracer` installs its own initializer before
      // calling this block - so the first touch has to happen inside. Anything reading `TraceManager` earlier in
      // `main` would bind it to the default processor, leave `withTracer`'s `TraceFileSpanExporter` writing a file
      // nothing flushes, and make this call flush a processor with no spans in it. The failure is not silent - the
      // trace file arrives with no root and `dev-dist trace` reports it as unusable rather than joining it - but it
      // is remote from its cause, so: do not read `TraceManager` before `runDevDistJob`.
      TraceManager.flush()
    }
    return
  }

  if (consoleSpansWhenNotMeasuring) {
    block()
    return
  }
  withoutTracer(block)
}

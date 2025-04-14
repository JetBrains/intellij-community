// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker.core.output

import io.opentelemetry.api.trace.Tracer
import org.jetbrains.bazel.jvm.span
import java.nio.file.Path

suspend fun writeJarAndAbi(
  tracer: Tracer,
  outputSink: OutputSink,
  outJar: Path,
  abiJar: Path?,
) {
  if (abiJar == null) {
    tracer.span("write output JAR") {
      outputSink.writeToZip(outJar = outJar)
    }
  }
  else {
    tracer.span("create output JAR and ABI JAR") {
      outputSink.createOutputAndAbi(outJar = outJar, abiJar = abiJar)
    }
  }
}


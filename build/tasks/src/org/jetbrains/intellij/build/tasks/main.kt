// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.trace.SpanBuilder
import org.jetbrains.intellij.build.io.download
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.function.*

fun expose(): Map<String, Any> {
  return java.util.Map.of(
    "createTask",
    BiFunction<SpanBuilder, Supplier<*>, ForkJoinTask<*>> { spanBuilder, task -> createTask(spanBuilder, task) },
    "span",
    BiConsumer<SpanBuilder, Runnable> { spanBuilder, task ->
      val span = spanBuilder.startSpan()
      span.makeCurrent().use {
        span.use {
          task.run()
        }
      }
    },
    "download",
    Function<String, ByteArray>(::download),
    "buildJars",
    BiConsumer<List<Triple<Path, String, List<Source>>>, Boolean>(::buildJars),
    "buildJar",
    BiConsumer<Path, List<Source>>(::buildJar),
    "createZipSource",
    BiFunction<Path, IntConsumer?, Any>(::createZipSource),
    "isLibraryMergeable",
    Predicate<String>(::isLibraryMergeable),
    "packInternalUtilities",
    BiConsumer<Path, List<Path>>(::packInternalUtilities),
  )
}

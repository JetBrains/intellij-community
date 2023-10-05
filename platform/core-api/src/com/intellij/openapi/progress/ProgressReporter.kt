// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION_ERROR", "DeprecatedCallableAddReplaceWith")

package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.platform.util.progress.*
import com.intellij.platform.util.progress.durationStep
import com.intellij.platform.util.progress.filterWithProgress
import com.intellij.platform.util.progress.forEachWithProgress
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.platform.util.progress.progressReporter
import com.intellij.platform.util.progress.progressStep
import com.intellij.platform.util.progress.rawProgressReporter
import com.intellij.platform.util.progress.transformWithProgress
import com.intellij.platform.util.progress.withRawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import com.intellij.openapi.progress.ProgressReporter as DeprecatedProgressReporter
import com.intellij.openapi.progress.RawProgressReporter as DeprecatedRawProgressReporter

@Deprecated("Moved to com.intellij.platform.util.progress", level = DeprecationLevel.ERROR)
interface ProgressReporter : ProgressReporter {

  @Deprecated("Moved to com.intellij.platform.util.progress.ProgressReporter")
  override fun rawReporter(): DeprecatedRawProgressReporter
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("indeterminateStep(text, action)", "com.intellij.platform.util.progress.indeterminateStep"),
  DeprecationLevel.ERROR,
)
suspend fun <T> indeterminateStep(
  text: @ProgressText String? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  return indeterminateStep(text, action)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("progressStep(endFraction, text, action)", "com.intellij.platform.util.progress.progressStep"),
  DeprecationLevel.ERROR,
)
suspend fun <T> progressStep(
  endFraction: Double,
  text: @ProgressText String? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  return progressStep(endFraction, text, action)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("durationStep(duration, text, action)", "com.intellij.platform.util.progress.durationStep"),
  DeprecationLevel.ERROR,
)
suspend fun <T> durationStep(
  duration: Double,
  text: @ProgressText String? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  return durationStep(duration, text, action)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("transformWithProgress(concurrent, transform)", "com.intellij.platform.util.progress.transformWithProgress"),
  DeprecationLevel.ERROR,
)
suspend fun <T, R> Collection<T>.transformWithProgress(
  concurrent: Boolean,
  transform: suspend (value: T, out: suspend (R) -> Unit) -> Unit,
): List<R> {
  return transformWithProgress(concurrent, transform)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("mapWithProgress(concurrent, mapper)", "com.intellij.platform.util.progress.mapWithProgress"),
  DeprecationLevel.ERROR,
)
suspend fun <T, R> Collection<T>.mapWithProgress(concurrent: Boolean, mapper: suspend (value: T) -> R): List<R> {
  return mapWithProgress(concurrent, mapper)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("filterWithProgress(concurrent, predicate)", "com.intellij.platform.util.progress.filterWithProgress"),
  DeprecationLevel.ERROR,
)
suspend fun <T> Collection<T>.filterWithProgress(concurrent: Boolean, predicate: suspend (value: T) -> Boolean): List<T> {
  return filterWithProgress(concurrent, predicate)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("forEachWithProgress(concurrent, action)", "com.intellij.platform.util.progress.forEachWithProgress"),
  DeprecationLevel.ERROR,
)
suspend fun <T> Collection<T>.forEachWithProgress(concurrent: Boolean, action: suspend (value: T) -> Unit) {
  forEachWithProgress(concurrent, action)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("withRawProgressReporter(action)", "com.intellij.platform.util.progress.withRawProgressReporter"),
  DeprecationLevel.ERROR,
)
suspend fun <X> withRawProgressReporter(action: suspend CoroutineScope.() -> X): X {
  return withRawProgressReporter(action)
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("progressReporter", "com.intellij.platform.util.progress.progressReporter"),
  DeprecationLevel.ERROR,
)
val CoroutineContext.progressReporter: DeprecatedProgressReporter? get() = progressReporter?.asDeprecatedProgressReporter()

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("progressReporter", "com.intellij.platform.util.progress.progressReporter"),
  DeprecationLevel.ERROR,
)
val CoroutineScope.progressReporter: DeprecatedProgressReporter? get() = progressReporter?.asDeprecatedProgressReporter()

@Suppress("HardCodedStringLiteral", "OVERRIDE_DEPRECATION")
private fun ProgressReporter.asDeprecatedProgressReporter() = object : DeprecatedProgressReporter {
  override fun step(endFraction: Double, text: String?) = this@asDeprecatedProgressReporter.step(endFraction, text)
  override fun durationStep(duration: Double, text: String?) = this@asDeprecatedProgressReporter.durationStep(duration, text)
  override fun close() = this@asDeprecatedProgressReporter.close()
  override fun rawReporter() = this@asDeprecatedProgressReporter.rawReporter().asDeprecatedRawReporter()
}

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("rawProgressReporter", "com.intellij.platform.util.progress.rawProgressReporter"),
  DeprecationLevel.ERROR,
)
val CoroutineContext.rawProgressReporter: DeprecatedRawProgressReporter? get() = rawProgressReporter?.asDeprecatedRawReporter()

@Deprecated(
  "Moved to com.intellij.platform.util.progress",
  ReplaceWith("rawProgressReporter", "com.intellij.platform.util.progress.rawProgressReporter"),
  DeprecationLevel.ERROR,
)
val CoroutineScope.rawProgressReporter: DeprecatedRawProgressReporter? get() = rawProgressReporter?.asDeprecatedRawReporter()

@Suppress("HardCodedStringLiteral")
private fun RawProgressReporter.asDeprecatedRawReporter() = object : DeprecatedRawProgressReporter {
  override fun text(text: String?) = this@asDeprecatedRawReporter.text(text)
  override fun details(details: String?) = this@asDeprecatedRawReporter.details(details)
  override fun fraction(fraction: Double?) = this@asDeprecatedRawReporter.fraction(fraction)
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION_ERROR", "DeprecatedCallableAddReplaceWith")

package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.platform.util.progress.durationStep
import com.intellij.platform.util.progress.forEachWithProgress
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.platform.util.progress.progressStep
import com.intellij.platform.util.progress.withRawProgressReporter
import kotlinx.coroutines.CoroutineScope

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

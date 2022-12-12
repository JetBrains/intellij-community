// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Represents an entity used to send progress updates to.
 * The client code is not supposed to read the progress updates, thus there are no getters.
 *
 * Example usage:
 * ```
 * withBackgroundProgressIndicator(title = "Top Level", ...) {
 *   indeterminateStep("Indeterminate Stage") { ... }
 *   progressStep(text = "0.3", endFraction = 0.3) { ... }
 *   progressStep(text = "0.7) { // endFraction is 1.0 by default
 *     progressStep(endFraction = 0.4) { ... }
 *     progressStep {
 *       items.mapParallel { item ->
 *         progressStep(text = "Processing '${item.presentableText}'") {
 *           ...
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 * yields:
 * ```
 * |                     Top Level                           |
 * |  Indeterminate Stage  | 0.3  |         0.7              |
 *                                | 0.4       | 10 items     |
 * ```
 *
 * ### Legend
 * A step is called "indeterminate" if its duration in the parent reporter is unknown.
 *
 * ### Lifecycle
 * A reporter starts in indeterminate state (internal fraction is -1.0).
 * The start of the first determinate child step (i.e. a child step with end fraction >= 0.0)
 * triggers the transition of the current reporter to the determinate state (internal fraction is 0.0).
 * The start of an indeterminate child step does not affect the fraction of the current reporter.
 *
 * Indeterminate and determinate child steps can go in any order.
 *
 * Finally, [close] transitions the reporter to the final state (internal fraction is 1.0).
 *
 * ### Fraction Scaling
 *
 * Each reporter fraction spans from 0.0 to 1.0.
 * Each child reporter fraction also spans from 0.0 to 1.0,
 * and it's scaled to the parent using the end fraction passed to create a given child step.
 * ```
 * | 0.0                    0.4                                     1.0 |
 * | 0.0    child 1      1.0 | 0.0               child 2            1.0 |
 * ```
 *
 * ### Concurrency
 *
 * Child steps of the current reporter are allowed to exist concurrently.
 * The text of the current reporter is the last reported text of any the child steps.
 * The fraction of the current reporter is a sum of scaled child fractions.
 *
 * To reason about the growth of end fraction, each end fraction is expected to be greater than the previous one.
 * For example, the following might throw depending on execution order:
 * ```
 * fun CoroutineScope.run(topLevelStep: ProgressReporter) {
 *   launch {
 *     // will throw if executed after creating a child step with endFraction = 1.0
 *     topLevelStep.progressStep(endFraction = 0.5) { ... }
 *   }
 *   launch {
 *     topLevelStep.progressStep(endFraction = 1.0) { ... }
 *   }
 * }
 * ```
 * Instead, concurrent child steps should be created by specifying the duration:
 * ```
 * fun CoroutineScope.run(topLevelStep: ProgressReporter) {
 *   launch {
 *     // note duration parameter
 *     topLevelStep.durationStep(duration = 0.5) { ... }
 *   }
 *   launch {
 *     topLevelStep.durationStep(duration = 0.5) { ... }
 *   }
 * }
 * ```
 *
 * ### Examples
 *
 * #### How to process a list sequentially
 * ```
 * val items: List<X> = ...
 * withBackgroundProgressIndicator(...) {
 *   items.mapWithProgress {
 *     // will show the item string as progress text in the UI
 *     progressStep(text = item.presentableString()) {
 *       ...
 *     }
 *   }
 *   // or
 *   progressStep(text = "Processing items", endFraction = ...) {
 *     items.mapWithProgress {
 *       // will show the item string as progress details in the UI
 *       progressStep(text = item.presentableString()) {
 *         ...
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * #### How to process a list in parallel
 * ```
 * val items: List<X> = ...
 * withBackgroundProgressIndicator(...) {
 *   items.mapParallelWithProgress {
 *     // will show the item string as progress text in the UI
 *     progressStep(text = item.presentableString()) {
 *       ...
 *     }
 *   }
 *   // or
 *   progressStep(text = "Processing items", endFraction = ...) {
 *     items.mapParallelWithProgress {
 *       // will show the item string as progress details in the UI
 *       progressStep(text = item.presentableString()) {
 *         ...
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @see com.intellij.openapi.progress.impl.TextDetailsProgressReporter
 */
@Experimental
@NonExtendable
interface ProgressReporter : AutoCloseable {

  /**
   * Starts a child step.
   *
   * @param text text of the current step.
   * If the text is `null`, then the returned child step text will be used as text of this reporter.
   * If the text is not `null`, then the text will be used as text of this reporter,
   * while the text of the returned child step will be used as details of this reporter.
   *
   * @param endFraction value greater than 0.0 and less or equal to 1.0,
   * which is used to advance the fraction of the current step after the returned child step is [closed][close],
   * or `null` if this step is indeterminate, in which case the fraction advancements in the returned step will be ignored in this reporter.
   * If [endFraction] is not `null, then the duration of the returned step
   * would be the difference between the previous [endFraction] and the currently requested one,
   * which means that each subsequent call should request [endFraction] greater than the previously requested [endFraction].
   *
   * @see close
   */
  fun step(text: @ProgressText String?, endFraction: Double?): ProgressReporter

  /**
   * Starts a child step.
   *
   * @param duration duration of the step relative to this reporter.
   * It's used to advance the fraction of the current step after the returned child step is [closed][close].
   * The sum of durations of all child steps cannot exceed 1.0.
   *
   * @param text text of the current step.
   * If the text is `null`, then the returned child step text will be used as text of this reporter.
   * If the text is not `null`, then the text will be used as text of this reporter,
   * while the text of the returned child step will be used as details of this reporter.
   */
  fun durationStep(duration: Double, text: @ProgressText String?): ProgressReporter

  /**
   * Marks current step as completed.
   * This usually causes the progress fraction to advance in the UI.
   *
   * **It's mandatory to call this method**. Internally, this method unsubscribes from [child step][step] updates.
   *
   * Example:
   * ```
   * runUnderProgress { topLevelStep ->
   *   val childStep = topLevelStep.step("Part 1", 0.5)
   *   try {
   *     // do stuff
   *   }
   *   finally {
   *     childStep.close() // will advance the fraction to 0.5
   *   }
   * }
   * ```
   */
  override fun close()

  /**
   * Makes this reporter raw.
   * A raw reporter cannot start child steps.
   * A reporter which has child steps cannot be made raw.
   * This function cannot be called twice.
   *
   * @return a handle to feed the progress updates
   */
  fun rawReporter(): RawProgressReporter

  companion object {

    @JvmStatic
    fun <T> ProgressReporter.indeterminateStep(
      text: @ProgressText String?,
      action: ProgressReporter.() -> T,
    ): T = step(text, endFraction = null).use(action)

    @JvmStatic
    fun <T> ProgressReporter.progressStep(
      text: @ProgressText String?,
      endFraction: Double = 1.0,
      action: ProgressReporter.() -> T,
    ): T = step(text, endFraction).use(action)

    @JvmStatic
    fun <T, R> ProgressReporter.mapWithProgress(
      items: List<T>,
      mapper: ProgressReporter.(T) -> R,
    ): List<R> {
      val itemDuration = 1.0 / items.size
      return items.map { item ->
        durationStep(itemDuration, text = null).use { itemStep ->
          itemStep.mapper(item)
        }
      }
    }
  }
}

suspend fun <T> indeterminateStep(
  text: @ProgressText String? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  val reporter = coroutineContext.progressReporter
                 ?: return coroutineScope(action)
  return progressStep(reporter, text, endFraction = null, action)
}

suspend fun <T> progressStep(
  text: @ProgressText String? = null,
  endFraction: Double = 1.0,
  action: suspend CoroutineScope.() -> T,
): T {
  val reporter = coroutineContext.progressReporter
                 ?: return coroutineScope(action)
  return progressStep(reporter, text, endFraction, action)
}

private suspend fun <T> progressStep(
  parent: ProgressReporter,
  text: @ProgressText String?,
  endFraction: Double?,
  action: suspend CoroutineScope.() -> T,
): T {
  return parent.step(text, endFraction).use { step: ProgressReporter ->
    withContext(step.asContextElement(), action)
  }
}

suspend fun <T> durationStep(duration: Double, text: @ProgressText String? = null, action: suspend CoroutineScope.() -> T): T {
  val reporter = coroutineContext.progressReporter
                 ?: return coroutineScope(action)
  return reporter.durationStep(duration, text).use { step ->
    withContext(step.asContextElement(), action)
  }
}

suspend fun <T, R> List<T>.mapWithProgress(mapper: suspend CoroutineScope.(T) -> R): List<R> = coroutineScope {
  val items = this@mapWithProgress
  val reporter = progressReporter
  if (reporter == null) {
    items.map {
      mapper(it)
    }
  }
  else {
    val duration = 1.0 / size
    items.map { item ->
      reporter.durationStep(duration, text = null).use { durationStep ->
        withContext(durationStep.asContextElement()) {
          mapper(item)
        }
      }
    }
  }
}

suspend fun <T, R> List<T>.mapParallelWithProgress(mapper: suspend CoroutineScope.(T) -> R): List<R> = coroutineScope {
  val reporter = progressReporter
  val deferredList: List<Deferred<R>> = if (reporter == null) {
    map { item ->
      async {
        mapper(item)
      }
    }
  }
  else {
    val duration = 1.0 / size
    map { item ->
      val step = reporter.durationStep(duration, text = null)
      async(step.asContextElement()) {
        step.use {
          mapper(item)
        }
      }
    }
  }
  deferredList.awaitAll()
}

/**
 * Switches from context [ProgressReporter] to [RawProgressReporter] via [ProgressReporter.rawReporter].
 * This means, that the context [ProgressReporter] is marked raw.
 * If the context [ProgressReporter] already has children, then the caller should wrap this call
 * into [progressStep] or [indeterminateStep] to start a new child progress step, which then can be marked raw.
 *
 * The [action] loses [progressReporter] and receives [rawProgressReporter] in its context instead.
 */
suspend fun <X> withRawProgressReporter(action: suspend CoroutineScope.() -> X): X {
  val progressReporter = coroutineContext.progressReporter
                         ?: return coroutineScope(action)
  return withContext(progressReporter.rawReporter().asContextElement(), action)
}

fun ProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Step(this)
val CoroutineContext.progressReporter: ProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Step)?.reporter
val CoroutineScope.progressReporter: ProgressReporter? get() = coroutineContext.progressReporter

fun RawProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Raw(this)
val CoroutineContext.rawProgressReporter: RawProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Raw)?.reporter
val CoroutineScope.rawProgressReporter: RawProgressReporter? get() = coroutineContext.rawProgressReporter

private sealed class ProgressReporterElement : AbstractCoroutineContextElement(ProgressReporterElement) {
  companion object : CoroutineContext.Key<ProgressReporterElement>
  class Step(val reporter: ProgressReporter) : ProgressReporterElement()
  class Raw(val reporter: RawProgressReporter) : ProgressReporterElement()
}

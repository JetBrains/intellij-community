// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
 * Finally, [finish] transitions the reporter to the final state (internal fraction is 1.0).
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
 * Concurrent steps should be created in a sequential way to reason about the growth of end fraction.
 * For example, the following might throw depending on execution order:
 * ```
 * fun CoroutineScope.run(topLevelStep: ProgressReporter) {
 *   launch {
 *     topLevelStep.progressStep(endFraction = 0.5) { ... }
 *   }
 *   launch {
 *     topLevelStep.progressStep(endFraction = 1.0) { ... }
 *   }
 * }
 * ```
 *
 * The correct way:
 * ```
 * fun CoroutineScope.run(topLevelStep: ProgressReporter) {
 *   val step1 = topLevelStep.step(endFraction = 0.5)
 *   launch {
 *     try {
 *       ...
 *     }
 *     finally {
 *       step1.finish()
 *     }
 *   }
 *   // Note, step2 is created strictly after step1 sequentially.
 *   // After creation, both can report their state and can be finished concurrently.
 *   val step2 = topLevelStep.step(endFraction = 1.0)
 *   launch {
 *     try {
 *       ...
 *     }
 *     finally {
 *       step2.finish()
 *     }
 *   }
 * }
 * ```
 *
 * @see com.intellij.openapi.progress.impl.TextDetailsProgressReporter
 */
@Experimental
@NonExtendable
interface ProgressReporter {

  /**
   * Starts a child step.
   *
   * @param text text of the current step.
   * If the text is `null`, then the returned child step text will be used as text of this reporter.
   * If the text is not `null`, then the text will be used as text of this reporter,
   * while the text of the returned child step will be used as details of this reporter.
   *
   * @param endFraction value which is used to advance the fraction of the current step after the returned child step is [finished][finish],
   * or `null` if this step is indeterminate, in which case the fraction advancements in the returned step will be ignored in this reporter
   *
   * @see finish
   */
  fun step(text: @ProgressText String?, endFraction: Double?): ProgressReporter

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
   *     childStep.finish() // will advance the fraction to 0.5
   *   }
   * }
   * ```
   */
  fun finish()

  companion object {

    @JvmStatic
    fun <T> ProgressReporter.indeterminateStep(
      text: @ProgressText String?,
      action: ProgressReporter.() -> T,
    ): T = progressStepInner(parent = this, text, endFraction = null, action)

    @JvmStatic
    fun <T> ProgressReporter.progressStep(
      text: @ProgressText String?,
      endFraction: Double = 1.0,
      action: ProgressReporter.() -> T,
    ): T = progressStepInner(parent = this, text, endFraction, action)
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
  return progressStepInner(parent, text, endFraction) { step ->
    withContext(step.asContextElement(), action)
  }
}

private inline fun <T> progressStepInner(
  parent: ProgressReporter,
  text: @ProgressText String?,
  endFraction: Double?,
  action: (child: ProgressReporter) -> T,
): T {
  val step = parent.step(text, endFraction)
  try {
    return action(step)
  }
  finally {
    step.finish()
  }
}

fun ProgressReporter.asContextElement(): CoroutineContext.Element = ProgressStepElement(this)

val CoroutineContext.progressReporter: ProgressReporter? get() = this[ProgressStepElement]?.reporter

val CoroutineScope.progressReporter: ProgressReporter? get() = coroutineContext.progressReporter

private class ProgressStepElement(val reporter: ProgressReporter) : AbstractCoroutineContextElement(ProgressStepElement) {
  companion object : CoroutineContext.Key<ProgressStepElement>
}

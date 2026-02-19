// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.delay
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.asJavaRandom

suspend fun <T> retryWithExponentialBackOff(
  attempts: Int = 5,
  initialDelayMs: Long = TimeUnit.SECONDS.toMillis(5),
  backOffLimitMs: Long = TimeUnit.MINUTES.toMillis(3),
  backOffFactor: Int = 2, backOffJitter: Double = 0.1,
  onException: suspend (attempt: Int, e: Exception) -> Unit = { attempt, e -> defaultExceptionConsumer(attempt, e) },
  isRetryAllowed: suspend (e: Exception) -> Boolean = { true },
  action: suspend (attempt: Int) -> T
): T {
  var effectiveDelay = initialDelayMs
  val exceptions = mutableListOf<Exception>()
  for (attempt in 1..attempts) try {
    return action(attempt)
  }
  catch (e: Exception) {
    onException(attempt, e)
    if ((e is IExceptionWithRetryPolicy && e.isRetryAllowed.not()) || isRetryAllowed(e).not()) {
      throw Exception("Attempt $attempt failed, stopping retries", e).apply {
        exceptions.forEach(this::addSuppressed)
      }
    }
    exceptions += if (effectiveDelay > 0) {
      Exception("Attempt $attempt failed with '${e.message}', retrying in ${effectiveDelay}ms", e)
    }
    else e
    if (attempt == attempts) {
      throw Exception("$attempts attempts failed, see suppressed exceptions for details").apply {
        exceptions.forEach(this::addSuppressed)
      }
    }
    if (effectiveDelay > 0) {
      delay(effectiveDelay)
    }
    effectiveDelay = nextDelay(
      previousDelay = effectiveDelay,
      backOffLimitMs = backOffLimitMs,
      backOffFactor = backOffFactor,
      backOffJitter = backOffJitter,
      exceptions = exceptions
    )
  }
  error("Should never be reached")
}

private fun defaultExceptionConsumer(attempt: Int, e: Exception) {
  val spanMessage = if (e is IExceptionWithRetryPolicy && e.isRetryAllowed.not()) "Exception is not retryable" else "Retrying action with exponential back off"
  BuildDependenciesDownloader.TRACER.spanBuilder(spanMessage).startSpan().use { span ->
    span.addEvent("Attempt failed", Attributes.of(
      AttributeKey.longKey("attemptNumber"), attempt.toLong(),
      AttributeKey.stringKey("error"), e.toString()
    ))
  }
}

private fun nextDelay(
  previousDelay: Long,
  backOffLimitMs: Long,
  backOffFactor: Int,
  backOffJitter: Double,
  exceptions: List<Exception>
): Long {
  val nextDelay = min(previousDelay * backOffFactor, backOffLimitMs) + (Random.asJavaRandom().nextGaussian() * previousDelay * backOffJitter).toLong()
  if (nextDelay > backOffLimitMs) {
    throw Exception("Back off limit ${backOffLimitMs}ms exceeded, see suppressed exceptions for details").apply {
      exceptions.forEach(this::addSuppressed)
    }
  }
  return nextDelay
}

interface IExceptionWithRetryPolicy{
  val isRetryAllowed: Boolean
}

class NoMoreRetriesException(message: String): Exception(message), IExceptionWithRetryPolicy {
  override val isRetryAllowed: Boolean = false
}
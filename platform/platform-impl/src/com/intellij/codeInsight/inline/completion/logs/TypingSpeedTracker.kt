// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.ApiStatus
import java.time.Instant
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@ApiStatus.Internal
object TypingSpeedTracker {
  private const val LAST_TYPING_TIMESTAMP_KEY: String = "last_typing_timestamp"
  private const val TYPING_SPEED_KEY_PREFIX: String = "typing_speed_"
  private val DECAY_DURATIONS = listOf(1, 2, 5, 30).map { it.seconds }
  val properties: PropertiesComponent
    get() = PropertiesComponent.getInstance()

  private var lastTypingTimestamp: Long?
    get() = properties.getValue(LAST_TYPING_TIMESTAMP_KEY)?.let { java.lang.Long.parseLong(it) }
    private set(value) = properties.setValue(LAST_TYPING_TIMESTAMP_KEY, value?.toString())

  fun getTimeSinceLastTyping(currentTimestamp: Long = Instant.now().toEpochMilli()) = lastTypingTimestamp?.let { currentTimestamp - it }

  fun getTypingSpeed(duration: Duration): Float? = properties.getValue(getTypingSpeedKey(duration))?.let { java.lang.Float.parseFloat(it) }
  private fun setTypingSpeed(duration: Duration, speed: Float) = properties.setValue(getTypingSpeedKey(duration), speed.toString())
  private fun getTypingSpeedKey(duration: Duration): String {
    assert(duration in DECAY_DURATIONS)
    return TYPING_SPEED_KEY_PREFIX + duration.toString(DurationUnit.SECONDS)
  }

  fun typingOccurred(currentTimestamp: Long = Instant.now().toEpochMilli()) {
    val duration = getTimeSinceLastTyping(currentTimestamp)
    if (duration != null) {
      val lastSpeed = 60 * 1000 / (duration + 1).toFloat()  // Symbols per minute
      for (decayDuration in DECAY_DURATIONS) {
        val typingSpeed = getTypingSpeed(decayDuration) ?: 0F
        val alpha = decayingFactor(duration, decayDuration)
        setTypingSpeed(decayDuration, alpha * typingSpeed + (1 - alpha) * lastSpeed)
      }
    }
    lastTypingTimestamp = currentTimestamp
  }

  private fun decayingFactor(duration: Long, decayDuration: Duration): Float =
    if (decayDuration == Duration.ZERO) 0F
    else 0.5.pow(duration / decayDuration.toDouble(DurationUnit.MILLISECONDS)).toFloat()
}


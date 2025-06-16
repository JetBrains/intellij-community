// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ml.feature.Feature
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.time.Instant
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import com.intellij.platform.ml.feature.FeatureDeclaration as OldFeatureDeclaration
import com.intellij.codeInsight.inline.completion.logs.statistics.TimeBetweenTypingComponent
import com.intellij.codeInsight.inline.completion.logs.statistics.UserStatisticConstants.MAX_TYPING_INTERVAL

@ApiStatus.Internal
@Service
class TypingSpeedTracker {
  private var lastTypingTimestamp: Long? = null
  private val typingSpeeds: MutableMap<Duration, Float> = DECAY_DURATIONS.mapValues { 0F }.toMutableMap()

  fun getTimeSinceLastTyping(currentTimestamp: Long = Instant.now().toEpochMilli()) = lastTypingTimestamp?.let { currentTimestamp - it }

  fun typingOccurred(currentTimestamp: Long = Instant.now().toEpochMilli()) {
    val duration = getTimeSinceLastTyping(currentTimestamp)
    if (duration != null && duration > 0) {
      val lastSpeed = 60 * 1000 / duration.toFloat()  // Symbols per minute
      for ((decayDuration, typingSpeed) in typingSpeeds) {
        val alpha = decayingFactor(duration, decayDuration)
        typingSpeeds[decayDuration] = alpha * typingSpeed + (1 - alpha) * lastSpeed
      }

      if (duration < MAX_TYPING_INTERVAL.inWholeMilliseconds)
        TimeBetweenTypingComponent.getInstance().fireTypingPerformed(duration)
    }
    lastTypingTimestamp = currentTimestamp
  }

  private fun decayingFactor(duration: Long, decayDuration: Duration): Float =
    if (decayDuration == Duration.ZERO) 0F
    else 0.5.pow(duration / decayDuration.toDouble(DurationUnit.MILLISECONDS)).toFloat()

  fun getTypingSpeedEventPairs(): Collection<Pair<EventPair<*>, Feature>> = DECAY_DURATIONS.mapNotNull { (decayDuration, eventFieldAndFeature) ->
    typingSpeeds[decayDuration]?.let {
      (eventFieldAndFeature.first with it) to (eventFieldAndFeature.second with it)
    }
  }

  @TestOnly
  fun getTypingSpeed(decayDuration: Duration): Float? = typingSpeeds[decayDuration]

  class KeyListener : KeyAdapter() {
    override fun keyReleased(event: KeyEvent) {
      if (!isValuableKey(event.keyChar)) {
        return
      }
      LOG.trace("Valuable key released event $event")
      getInstance().typingOccurred()
    }

    private fun isValuableKey(keyChar: Char): Boolean {
      return keyChar.isLetterOrDigit() ||
             keyChar.isWhitespace() ||
             keyChar in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{¦}~"
    }

    companion object {
      private val LOG = thisLogger()
    }
  }

  companion object {
    private val DECAY_DURATIONS = listOf(1, 2, 5, 30)
      .associate {
        it.seconds to Pair(
          EventFields.Float("typing_speed_${it}s", """
            Typing speed with exponential smoothing factor derived from decay_duration $it seconds as 
            `alpha = 0.5.pow(time_since_last_typing / decay_duration)`.
            Last speed (typings per minute) is computed as:
            `V_last = 60 / time_since_last_typing`
            And resulting formula:
            `V_avg = alpha * V_avg_previous + (1 - alpha) * V_last`
            Note: it's an alternative for moving average for time series. The lesser decay_duration the bigger weight recent typing speed gets in the averaging. 
            """.trimIndent()
          ),
          OldFeatureDeclaration.float("typing_speed_${it}s").nullable())
      }

    fun getInstance(): TypingSpeedTracker = service()
    fun getEventFields(): Array<EventField<*>> = DECAY_DURATIONS.values.map { it.first }.toTypedArray()
    fun getFeatures(): Set<OldFeatureDeclaration<*>> = DECAY_DURATIONS.values.map { it.second }.toSet()
  }
}

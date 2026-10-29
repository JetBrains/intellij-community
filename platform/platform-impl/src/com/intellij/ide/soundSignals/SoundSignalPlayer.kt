// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilityUsageTrackerCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.playSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
abstract class SoundSignalPlayer {
  companion object {
    @JvmStatic
    fun getInstance(): SoundSignalPlayer = service()
  }

  // In split mode the frontend plays the signal; the backend may have no audio device at all.
  fun play(vararg signals: SoundSignal) {
    if (signals.isEmpty() || IdeProductMode.isBackend) return
    val enabled = signals.filter { it.sounds.isNotEmpty() && isSoundSignalOn(it) }.distinctBy { it.id }
    if (enabled.isEmpty()) return
    playEnabled(enabled.associateWith { it.sounds.first() })
    for (signal in enabled) {
      AccessibilityUsageTrackerCollector.SOUND_SIGNAL_PLAYED.log(signal.id)
    }
  }

  fun play(signal: SoundSignal, sound: String) {
    if (IdeProductMode.isBackend || !isSoundSignalOn(signal)) return
    playEnabled(mapOf(signal to sound))
    AccessibilityUsageTrackerCollector.SOUND_SIGNAL_PLAYED.log(signal.id)
  }

  fun preview(vararg signals: SoundSignal) {
    if (signals.isNotEmpty() && !IdeProductMode.isBackend) playPreview(signals.distinctBy { it.id }.flatMap { signal -> signal.preview.map { signal to it } })
  }

  protected abstract fun playEnabled(sounds: Map<SoundSignal, String>)

  protected abstract fun playPreview(sounds: List<Pair<SoundSignal, String>>)
}

internal class SoundSignalPlayerImpl(private val scope: CoroutineScope) : SoundSignalPlayer() {
  // Keyed by the owner too, because two providers can use the same path for different sounds.
  private val audioBytesCache: ConcurrentMap<Pair<Class<*>, String>, ByteArray> = CollectionFactory.createConcurrentSoftValueMap()
  private val previewJob = AtomicReference<Job?>()

  override fun playEnabled(sounds: Map<SoundSignal, String>) {
    for ((signal, sound) in sounds) {
      scope.launch { playLogged(signal, sound) }
    }
  }

  override fun playPreview(sounds: List<Pair<SoundSignal, String>>) {
    if (sounds.isEmpty()) return
    previewJob.getAndSet(scope.launch {
      for ((index, pair) in sounds.withIndex()) {
        if (index > 0) delay(PREVIEW_DELAY)
        val (signal, sound) = pair
        playLogged(signal, sound)
      }
    })?.cancel()
  }

  private suspend fun playLogged(signal: SoundSignal, sound: String) {
    runCatching {
      if (!playSound { ByteArrayInputStream(audioBytes(signal, sound)) }) {
        LOG.warn("Failed to play the sound signal '${signal.id}' ($sound).")
      }
    }.getOrLogException(LOG)
  }

  private fun audioBytes(signal: SoundSignal, sound: String): ByteArray = audioBytesCache.computeIfAbsent(signal.ownerClass to sound) { (owner, path) ->
    val stream = checkNotNull(owner.classLoader.getResourceAsStream(path)) { "Sound resource not found: $path" }
    stream.use { it.readAllBytes() }
  }
}

private val LOG = logger<SoundSignalPlayerImpl>()

internal val PREVIEW_DELAY = 100.milliseconds

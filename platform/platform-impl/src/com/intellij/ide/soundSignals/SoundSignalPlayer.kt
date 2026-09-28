// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilityUsageTrackerCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.playSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentMap

@ApiStatus.Internal
abstract class SoundSignalPlayer {
  companion object {
    @JvmStatic
    fun getInstance(): SoundSignalPlayer = service()
  }

  fun play(vararg signals: SoundSignal) {
    if (signals.isEmpty()) return
    val enabled = signals.filter(::isSoundSignalOn).distinctBy { it.id }
    if (enabled.isEmpty()) return
    playEnabled(enabled)
    for (signal in enabled) {
      AccessibilityUsageTrackerCollector.SOUND_SIGNAL_PLAYED.log(signal.id)
    }
  }

  fun preview(vararg signals: SoundSignal) {
    if (signals.isNotEmpty()) playEnabled(signals.distinctBy { it.id })
  }

  protected abstract fun playEnabled(signals: Collection<SoundSignal>)
}

internal class SoundSignalPlayerImpl(private val scope: CoroutineScope) : SoundSignalPlayer() {
  // Keyed by the signal ID because two providers can use the same path for different sounds.
  private val audioBytesCache: ConcurrentMap<String, ByteArray> = CollectionFactory.createConcurrentSoftValueMap()

  override fun playEnabled(signals: Collection<SoundSignal>) {
    for (signal in signals) {
      scope.launch {
        runCatching {
          if (!playSound { ByteArrayInputStream(audioBytes(signal)) }) {
            LOG.warn("Failed to play the sound signal '${signal.id}' (${signal.resourcePath}).")
          }
        }.getOrLogException(LOG)
      }
    }
  }

  private fun audioBytes(signal: SoundSignal): ByteArray = audioBytesCache.computeIfAbsent(signal.id) {
    val path = signal.resourcePath
    val stream = checkNotNull(signal.ownerClass.classLoader.getResourceAsStream(path)) { "Sound resource not found: $path" }
    stream.use { it.readAllBytes() }
  }
}

private val LOG = logger<SoundSignalPlayerImpl>()

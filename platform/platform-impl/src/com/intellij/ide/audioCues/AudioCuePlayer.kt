// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

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
abstract class AudioCuePlayer {
  companion object {
    @JvmStatic
    fun getInstance(): AudioCuePlayer = service()
  }

  fun play(vararg cues: AudioCue) {
    if (cues.isEmpty()) return
    val settings = service<AudioCuesSettings>()
    val enabled = cues.filter(settings::isCueEnabled).distinctBy { it.id }
    if (enabled.isEmpty()) return
    playEnabled(enabled)
    for (cue in enabled) {
      AccessibilityUsageTrackerCollector.AUDIO_CUE_PLAYED.log(cue.id)
    }
  }

  internal fun preview(vararg cues: AudioCue) {
    if (cues.isNotEmpty()) playEnabled(cues.distinctBy { it.id })
  }

  protected abstract fun playEnabled(cues: Collection<AudioCue>)
}

internal class AudioCuePlayerImpl(private val scope: CoroutineScope) : AudioCuePlayer() {
  // Keyed by the cue ID because two providers can use the same path for different sounds.
  private val audioBytesCache: ConcurrentMap<String, ByteArray> = CollectionFactory.createConcurrentSoftValueMap()

  override fun playEnabled(cues: Collection<AudioCue>) {
    for (cue in cues) {
      scope.launch {
        runCatching {
          if (!playSound { ByteArrayInputStream(audioBytes(cue)) }) {
            LOG.warn("Failed to play the audio cue '${cue.id}' (${cue.resourcePath}).")
          }
        }.getOrLogException(LOG)
      }
    }
  }

  private fun audioBytes(cue: AudioCue): ByteArray = audioBytesCache.computeIfAbsent(cue.id) {
    val path = cue.resourcePath
    val stream = checkNotNull(cue.ownerClass.classLoader.getResourceAsStream(path)) { "Sound resource not found: $path" }
    stream.use { it.readAllBytes() }
  }
}

private val LOG = logger<AudioCuePlayerImpl>()

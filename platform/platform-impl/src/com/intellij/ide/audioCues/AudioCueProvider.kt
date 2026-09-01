// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/** Publishes the audio cues that a plugin owns. */
@ApiStatus.Internal
interface AudioCueProvider {
  val audioCues: Collection<AudioCue>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<AudioCueProvider> = ExtensionPointName("com.intellij.audioCueProvider")
  }
}

internal fun getAudioCues(): List<AudioCue> {
  val cuesById = LinkedHashMap<String, AudioCue>()
  for (provider in AudioCueProvider.EP_NAME.extensionList) {
    for (cue in provider.audioCues) {
      if (cuesById.putIfAbsent(cue.id, cue) != null) {
        LOG.error(PluginException.createByClass("Duplicate audio cue id '${cue.id}'", null, provider.javaClass))
      }
    }
  }
  return cuesById.values.sortedWith(compareBy({ it.settingsOrder }, { it.id }))
}

internal fun findAudioCue(id: String): AudioCue? =
  AudioCueProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider -> provider.audioCues.find { it.id == id } }

private val LOG = logger<AudioCueProvider>()

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/** Publishes the sound signals that a plugin owns. */
@ApiStatus.Internal
interface SoundSignalProvider {
  val soundSignals: Collection<SoundSignal>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<SoundSignalProvider> = ExtensionPointName("com.intellij.soundSignalProvider")
  }
}

@ApiStatus.Internal
fun getSoundSignals(): List<SoundSignal> {
  val signalsById = LinkedHashMap<String, SoundSignal>()
  for (provider in SoundSignalProvider.EP_NAME.extensionList) {
    for (signal in provider.soundSignals) {
      if (signalsById.putIfAbsent(signal.id, signal) != null) {
        LOG.error(PluginException.createByClass("Duplicate sound signal id '${signal.id}'", null, provider.javaClass))
      }
    }
  }
  return signalsById.values.sortedWith(compareBy({ it.settingsOrder }, { it.id }))
}

internal fun findSoundSignal(id: String): SoundSignal? =
  SoundSignalProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider -> provider.soundSignals.find { it.id == id } }

private val LOG = logger<SoundSignalProvider>()

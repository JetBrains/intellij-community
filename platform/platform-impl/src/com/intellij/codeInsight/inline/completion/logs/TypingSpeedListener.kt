// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@ApiStatus.Internal
class TypingSpeedListener : KeyAdapter() {
  override fun keyReleased(event: KeyEvent) {
    if (!isValuableKeyReleased(event)) {
      return
    }
    LOG.trace("Valuable key released event $event")
    TypingSpeedTracker.getInstance().typingOccurred()
  }

  private fun isValuableKeyReleased(event: KeyEvent): Boolean {
    return event.keyChar in 'a'..'z' ||
           event.keyChar in 'A'..'Z' ||
           event.keyChar in '0'..'9' ||
           event.keyChar in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{¦}~"
  }

  companion object {
    private val LOG = thisLogger()
  }
}
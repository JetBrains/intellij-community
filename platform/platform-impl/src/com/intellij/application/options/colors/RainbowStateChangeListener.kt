// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.util.messages.Topic

/**
 * Listener interface for observing changes in the semantic markup state.
 *
 * Similar to {@code EditorColorsListener}, but notifies the subscriber only in case of
 * change in the semantic markup state (on/off) for any language, including the change in the inheritance state.
 */
interface RainbowStateChangeListener {
  companion object {
    @JvmStatic
    val TOPIC: Topic<RainbowStateChangeListener> = Topic(RainbowStateChangeListener::class.java.getSimpleName(), RainbowStateChangeListener::class.java)
  }

  /**
   * Notifies the subscribers that the global editor colors scheme has changes in the semantic markup state.
   *
   * @param scheme the scheme with the new semantic markup state.
   * @param rainbowOnLanguages The set of languages that have rainbow highlighting enabled.
   */
  fun onRainbowStateChanged(scheme: EditorColorsScheme, rainbowOnLanguages: Set<String>)
}
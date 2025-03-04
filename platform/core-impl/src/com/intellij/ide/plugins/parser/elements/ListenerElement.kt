// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

import com.intellij.ide.plugins.parser.elements.OS.Companion.convert
import com.intellij.util.messages.ListenerDescriptor

class ListenerElement(
  @JvmField val listenerClassName: String,
  @JvmField val topicClassName: String,
  @JvmField val activeInTestMode: Boolean,
  @JvmField val activeInHeadlessMode: Boolean,
  @JvmField val os: OS?,
) {
  companion object {
    fun ListenerElement.convert(): ListenerDescriptor = ListenerDescriptor(
      os?.convert(),
      listenerClassName,
      topicClassName,
      activeInTestMode,
      activeInHeadlessMode,
    )
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.util.messages.Topic
import java.util.*

interface GdprSettingsChangeListener : EventListener {
  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<GdprSettingsChangeListener> = Topic(GdprSettingsChangeListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
  fun consentWritten()
}

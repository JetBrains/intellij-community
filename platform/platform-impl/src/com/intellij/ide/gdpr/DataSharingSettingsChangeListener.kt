// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.util.messages.Topic
import java.util.*

interface DataSharingSettingsChangeListener : EventListener {
  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<DataSharingSettingsChangeListener> = Topic(DataSharingSettingsChangeListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
  fun consentWritten()
}

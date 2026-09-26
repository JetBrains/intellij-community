// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.Internal
interface DataSharingLocalSettingsChangeListener : EventListener {
  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<DataSharingLocalSettingsChangeListener> = Topic(
      DataSharingLocalSettingsChangeListener::class.java,
      Topic.BroadcastDirection.NONE,
      true
    )
  }

  fun consentWritten()
}

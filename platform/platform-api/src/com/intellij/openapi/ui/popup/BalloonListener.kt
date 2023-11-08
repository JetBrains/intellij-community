// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup

import com.intellij.util.messages.Topic

fun interface BalloonListener {
  fun balloonShown(balloon: Balloon)

  companion object {
    /**
     * Notification about showing balloon
     */
    @JvmStatic
    @Topic.AppLevel
    val TOPIC: Topic<BalloonListener> = Topic(BalloonListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
  }
}
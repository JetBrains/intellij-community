// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * @author Alexander Lobas
 *
 * DynamicBundle -> LocalizationUtil -> LocalizationStateService
 * We can't place TOPIC in LocalizationStateService, or we get NoClassDefFoundError.
 */
@Internal
class LocalizationListener {
  companion object {
    @Internal
    @Topic.AppLevel
    val UPDATE_TOPIC: Topic<Runnable> = Topic(Runnable::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
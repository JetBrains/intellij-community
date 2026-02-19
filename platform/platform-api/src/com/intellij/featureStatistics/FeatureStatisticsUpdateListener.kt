// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FeatureStatisticsUpdateListener {
  companion object {
    @JvmField
    val TOPIC: Topic<FeatureStatisticsUpdateListener> = Topic.create("featureStatisticsUpdate", FeatureStatisticsUpdateListener::class.java)
  }
  fun completionStatUpdated(spared: Int) {}
}
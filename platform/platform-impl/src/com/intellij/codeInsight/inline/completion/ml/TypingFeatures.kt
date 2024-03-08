// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.ml

import com.intellij.codeInsight.inline.completion.logs.TypingSpeedTracker
import com.intellij.platform.ml.*

internal class TypingFeatures : TierDescriptor.Default(TierTyping) {
  companion object {
    val TIME_SINCE_LAST_TYPING = FeatureDeclaration.long("time_since_last_typing")
  }

  override val descriptionDeclaration: Set<FeatureDeclaration<*>> = TypingSpeedTracker.getFeatures() + TIME_SINCE_LAST_TYPING

  override suspend fun describe(environment: Environment, usefulFeaturesFilter: FeatureFilter): Set<Feature> = buildSet {
    with(environment[TierTyping]) {
      val timeSinceLastTyping = getTimeSinceLastTyping()
      if (timeSinceLastTyping != null) {
        add(TIME_SINCE_LAST_TYPING.with(timeSinceLastTyping))
        addAll(getTypingSpeedEventPairs().map { it.second })
      }
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.ml

import com.intellij.codeInsight.inline.completion.logs.TypingSpeedTracker
import com.jetbrains.ml.*

internal class TypingSpeedFeatureProvider : FeatureProvider(MLUnitTyping) {
  object Features {
    val TIME_SINCE_LAST_TYPING = FeatureDeclaration.long("time_since_last_typing").nullable()
  }

  override val featureComputationPolicy = FeatureComputationPolicy(true, true)

  override suspend fun computeFeatures(units: MLUnitsMap, usefulFeaturesFilter: FeatureFilter): List<Feature> = buildList {
    with(units[MLUnitTyping]) {
      val timeSinceLastTyping = getTimeSinceLastTyping()
      if (timeSinceLastTyping != null) {
        add(Features.TIME_SINCE_LAST_TYPING.with(timeSinceLastTyping))
        addAll(getTypingSpeedNewEventPairs().map { it.second })
      }
    }
  }


  override val featureDeclarations = TypingSpeedTracker.getFeaturesNew() + Features.TIME_SINCE_LAST_TYPING
}

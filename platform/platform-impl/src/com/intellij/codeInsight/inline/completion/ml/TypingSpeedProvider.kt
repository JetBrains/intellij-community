// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.ml

import com.intellij.codeInsight.inline.completion.logs.TypingSpeedTracker
import com.intellij.platform.ml.Tier
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.environment.EnvironmentExtender

internal class TypingSpeedProvider : EnvironmentExtender<TypingSpeedTracker> {
  override val extendingTier: Tier<TypingSpeedTracker> = TierTyping

  override fun extend(environment: Environment): TypingSpeedTracker {
    return TypingSpeedTracker.getInstance()
  }

  override val requiredTiers: Set<Tier<*>> = emptySet()
}

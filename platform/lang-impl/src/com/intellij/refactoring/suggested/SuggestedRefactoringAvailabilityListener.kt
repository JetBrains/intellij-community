// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SuggestedRefactoringAvailabilityListener {
  fun cleared() {}
  fun disabled() {}
  fun updated(state: SuggestedRefactoringState) {}
  fun reset() {}

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<SuggestedRefactoringAvailabilityListener> =
      Topic(SuggestedRefactoringAvailabilityListener::class.java, Topic.BroadcastDirection.NONE)
  }
}

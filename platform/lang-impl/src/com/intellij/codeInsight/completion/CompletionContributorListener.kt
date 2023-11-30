// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CompletionContributorListener {
  fun beforeCompletionContributorThreadStarted(indicator: CompletionProgressIndicator,
                                               initContext: CompletionInitializationContextImpl)

  companion object {
    @Topic.AppLevel
    val TOPIC = Topic("CompletionContributorListener", CompletionContributorListener::class.java)
  }
}
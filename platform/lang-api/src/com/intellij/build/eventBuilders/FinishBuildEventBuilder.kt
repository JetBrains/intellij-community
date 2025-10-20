// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.*
import com.intellij.build.events.EventResult
import com.intellij.build.events.FinishBuildEvent
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@NonExtendable
interface FinishBuildEventBuilder {

  @CheckReturnValue // mandatory field
  fun withStartBuildId(startBuildId: Any): FinishBuildEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): FinishBuildEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): FinishBuildEventBuilder

  @CheckReturnValue // mandatory field
  fun withMessage(message: @Message String): FinishBuildEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): FinishBuildEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): FinishBuildEventBuilder

  @CheckReturnValue // mandatory field
  fun withResult(result: EventResult): FinishBuildEventBuilder

  fun build(): FinishBuildEvent
}

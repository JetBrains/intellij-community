// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.EventResult
import com.intellij.build.events.FinishEvent
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@Experimental
@NonExtendable
interface FinishEventBuilder {

  @CheckReturnValue // mandatory field
  fun withStartId(startId: Any): FinishEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): FinishEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): FinishEventBuilder

  @CheckReturnValue // mandatory field
  fun withMessage(message: @Message String): FinishEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): FinishEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): FinishEventBuilder

  @CheckReturnValue // mandatory field
  fun withResult(result: EventResult): FinishEventBuilder

  fun build(): FinishEvent
}

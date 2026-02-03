// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.FinishEvent
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@NonExtendable
interface FinishEventBuilder {

  @CheckReturnValue
  fun withParentId(parentId: Any?): FinishEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): FinishEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): FinishEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): FinishEventBuilder

  fun build(): FinishEvent
}

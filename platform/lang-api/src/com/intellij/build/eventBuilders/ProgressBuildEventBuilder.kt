// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.ProgressBuildEvent
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@NonExtendable
interface ProgressBuildEventBuilder {

  @CheckReturnValue
  fun withParentId(parentId: Any?): ProgressBuildEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): ProgressBuildEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): ProgressBuildEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): ProgressBuildEventBuilder

  @CheckReturnValue
  fun withTotal(total: Long?): ProgressBuildEventBuilder

  @CheckReturnValue
  fun withProgress(progress: Long?): ProgressBuildEventBuilder

  @CheckReturnValue
  fun withUnit(unit: String?): ProgressBuildEventBuilder

  fun build(): ProgressBuildEvent
}

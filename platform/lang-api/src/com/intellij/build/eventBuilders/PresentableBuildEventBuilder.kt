// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.PresentableBuildEvent
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.CheckReturnValue

@Experimental
interface PresentableBuildEventBuilder {

  @CheckReturnValue
  fun withId(id: Any?): PresentableBuildEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): PresentableBuildEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): PresentableBuildEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): PresentableBuildEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): PresentableBuildEventBuilder

  fun build(): PresentableBuildEvent
}

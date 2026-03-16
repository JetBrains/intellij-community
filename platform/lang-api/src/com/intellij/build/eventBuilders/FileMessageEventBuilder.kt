// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Title
import com.intellij.build.events.FileMessageEvent
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@NonExtendable
interface FileMessageEventBuilder {

  @CheckReturnValue
  fun withId(id: Any?): FileMessageEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): FileMessageEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): FileMessageEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): FileMessageEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): FileMessageEventBuilder

  @CheckReturnValue
  fun withGroup(group: @Title String?): FileMessageEventBuilder

  fun build(): FileMessageEvent
}

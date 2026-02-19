// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Title
import com.intellij.build.events.MessageEvent
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@NonExtendable
interface MessageEventBuilder {

  @CheckReturnValue
  fun withId(id: Any?): MessageEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): MessageEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): MessageEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): MessageEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): MessageEventBuilder

  @CheckReturnValue
  fun withGroup(group: @Title String?): MessageEventBuilder

  @CheckReturnValue
  fun withNavigatable(navigatable: Navigatable?): MessageEventBuilder

  fun build(): MessageEvent
}

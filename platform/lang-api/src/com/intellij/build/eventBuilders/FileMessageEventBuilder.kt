// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEventsNls.*
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
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

  @CheckReturnValue // mandatory field
  fun withMessage(message: @Message String): FileMessageEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): FileMessageEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): FileMessageEventBuilder

  @CheckReturnValue // mandatory field
  fun withKind(kind: MessageEvent.Kind): FileMessageEventBuilder

  @CheckReturnValue
  fun withGroup(group: @Title String?): FileMessageEventBuilder

  @CheckReturnValue // mandatory field
  fun withFilePosition(filePosition: FilePosition): FileMessageEventBuilder

  fun build(): FileMessageEvent
}

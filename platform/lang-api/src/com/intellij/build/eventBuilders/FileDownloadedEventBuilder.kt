// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.FileDownloadedEvent
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@Experimental
@NonExtendable
interface FileDownloadedEventBuilder {

  @CheckReturnValue // mandatory field
  fun withStartId(startId: Any): FileDownloadedEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): FileDownloadedEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): FileDownloadedEventBuilder

  @CheckReturnValue // mandatory field
  fun withMessage(message: @Message String): FileDownloadedEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): FileDownloadedEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): FileDownloadedEventBuilder

  @CheckReturnValue // mandatory field
  fun withDuration(duration: Long): FileDownloadedEventBuilder

  @CheckReturnValue // mandatory field
  fun withDownloadPath(downloadPath: String): FileDownloadedEventBuilder

  fun build(): FileDownloadedEvent
}

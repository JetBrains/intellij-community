// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.*
import com.intellij.build.events.FileDownloadEvent
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@Experimental
@NonExtendable
interface FileDownloadEventBuilder {

  @CheckReturnValue // mandatory field
  fun withStartId(startId: Any): FileDownloadEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): FileDownloadEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): FileDownloadEventBuilder

  @CheckReturnValue // mandatory field
  fun withMessage(message: @Message String): FileDownloadEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): FileDownloadEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): FileDownloadEventBuilder

  @CheckReturnValue
  fun withTotal(total: Long?): FileDownloadEventBuilder

  @CheckReturnValue
  fun withProgress(progress: Long?): FileDownloadEventBuilder

  @CheckReturnValue
  fun withUnit(unit: String?): FileDownloadEventBuilder

  @CheckReturnValue // mandatory field
  fun withFirstInGroup(isFirstInGroup: Boolean): FileDownloadEventBuilder

  @CheckReturnValue // mandatory field
  fun withDownloadPath(downloadPath: String): FileDownloadEventBuilder

  fun build(): FileDownloadEvent
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.FileDownloadedEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.impl.FileDownloadedEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileDownloadedEventBuilderImpl : FileDownloadedEventBuilder {

  private var startId: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var duration: Long? = null
  private var downloadPath: String? = null

  override fun withStartId(startId: Any): FileDownloadedEventBuilderImpl =
    apply { this.startId = startId }

  override fun withParentId(parentId: Any?): FileDownloadedEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FileDownloadedEventBuilderImpl =
    apply { this.time = time }

  override fun withMessage(message: @Message String): FileDownloadedEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): FileDownloadedEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FileDownloadedEventBuilderImpl =
    apply { this.description = description }

  override fun withDuration(duration: Long): FileDownloadedEventBuilderImpl =
    apply { this.duration = duration }

  override fun withDownloadPath(downloadPath: String): FileDownloadedEventBuilderImpl =
    apply { this.downloadPath = downloadPath }

  override fun build(): FileDownloadedEventImpl =
    FileDownloadedEventImpl(
      startId ?: throw IllegalStateException("The FileDownloadedEvent's 'startId' property should be defined"),
      parentId,
      time,
      message ?: throw IllegalStateException("The FileDownloadedEvent's 'message' property should be defined"),
      hint,
      description,
      duration ?: throw IllegalStateException("The FileDownloadedEvent's 'duration' property should be defined"),
      downloadPath ?: throw IllegalStateException("The FileDownloadedEvent's 'downloadPath' property should be defined")
    )
}

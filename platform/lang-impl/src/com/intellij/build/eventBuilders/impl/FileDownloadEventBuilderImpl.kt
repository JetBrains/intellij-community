// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.FileDownloadEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.impl.FileDownloadEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileDownloadEventBuilderImpl : FileDownloadEventBuilder {

  private var startId: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var total: Long? = null
  private var progress: Long? = null
  private var unit: String? = null

  private var isFirstInGroup: Boolean? = null
  private var downloadPath: String? = null

  override fun withStartId(startId: Any): FileDownloadEventBuilderImpl =
    apply { this.startId = startId }

  override fun withParentId(parentId: Any?): FileDownloadEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FileDownloadEventBuilderImpl =
    apply { this.time = time }

  override fun withMessage(message: @Message String): FileDownloadEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): FileDownloadEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FileDownloadEventBuilderImpl =
    apply { this.description = description }

  override fun withTotal(total: Long?): FileDownloadEventBuilderImpl =
    apply { this.total = total }

  override fun withProgress(progress: Long?): FileDownloadEventBuilderImpl =
    apply { this.progress = progress }

  override fun withUnit(unit: String?): FileDownloadEventBuilderImpl =
    apply { this.unit = unit }

  override fun withFirstInGroup(isFirstInGroup: Boolean): FileDownloadEventBuilderImpl =
    apply { this.isFirstInGroup = isFirstInGroup }

  override fun withDownloadPath(downloadPath: String): FileDownloadEventBuilderImpl =
    apply { this.downloadPath = downloadPath }

  override fun build(): FileDownloadEventImpl =
    FileDownloadEventImpl(
      startId ?: throw IllegalStateException("The FileDownloadEvent's 'startId' property should be defined"),
      parentId,
      time,
      message ?: throw IllegalStateException("The FileDownloadEvent's 'message' property should be defined"),
      hint,
      description,
      total,
      progress,
      unit,
      isFirstInGroup ?: throw IllegalStateException("The FileDownloadEvent's 'isFirstInGroup' property should be defined"),
      downloadPath ?: throw IllegalStateException("The FileDownloadEvent's 'downloadPath' property should be defined")
    )
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.FileDownloadedEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.impl.FileDownloadedEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileDownloadedEventBuilderImpl(
  private val startId: Any,
  private val message: @Message String,
  private val duration: Long,
  private val downloadPath: String,
) : FileDownloadedEventBuilder {

  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  override fun withParentId(parentId: Any?): FileDownloadedEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FileDownloadedEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): FileDownloadedEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FileDownloadedEventBuilderImpl =
    apply { this.description = description }

  override fun build(): FileDownloadedEventImpl =
    FileDownloadedEventImpl(startId, parentId, time, message, hint, description, duration, downloadPath)
}

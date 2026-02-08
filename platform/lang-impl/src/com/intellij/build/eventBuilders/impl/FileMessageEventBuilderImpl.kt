// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.FileMessageEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.BuildEventsNls.Title
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileMessageEventBuilderImpl(
  private val message: @Message String,
  private val kind: MessageEvent.Kind,
  private val filePosition: FilePosition
) : FileMessageEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var group: @Title String? = null

  override fun withId(id: Any?): FileMessageEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): FileMessageEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FileMessageEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): FileMessageEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FileMessageEventBuilderImpl =
    apply { this.description = description }

  override fun withGroup(group: @Title String?): FileMessageEventBuilderImpl =
    apply { this.group = group }

  override fun build(): FileMessageEventImpl =
    FileMessageEventImpl(id, parentId, time, message, hint, description, kind, group, filePosition)
}

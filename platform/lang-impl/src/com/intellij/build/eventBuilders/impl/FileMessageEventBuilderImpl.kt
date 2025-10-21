// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.FileMessageEventBuilder
import com.intellij.build.events.BuildEventsNls.*
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileMessageEventBuilderImpl : FileMessageEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var kind: MessageEvent.Kind? = null
  private var group: @Title String? = null

  private var filePosition: FilePosition? = null

  override fun withId(id: Any?): FileMessageEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): FileMessageEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FileMessageEventBuilderImpl =
    apply { this.time = time }

  override fun withMessage(message: @Message String): FileMessageEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): FileMessageEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FileMessageEventBuilderImpl =
    apply { this.description = description }

  override fun withKind(kind: MessageEvent.Kind): FileMessageEventBuilderImpl =
    apply { this.kind = kind }

  override fun withGroup(group: @Title String?): FileMessageEventBuilderImpl =
    apply { this.group = group }

  override fun withFilePosition(filePosition: FilePosition): FileMessageEventBuilderImpl =
    apply { this.filePosition = filePosition }

  override fun build(): FileMessageEventImpl =
    FileMessageEventImpl(
      id,
      parentId,
      time,
      message ?: throw IllegalStateException("The FileMessageEvent's 'message' property should be defined"),
      hint,
      description,
      kind ?: throw IllegalStateException("The FileMessageEvent's 'kind' property should be defined"),
      group,
      filePosition ?: throw IllegalStateException("The FileMessageEvent's 'filePosition' property should be defined")
    )
}

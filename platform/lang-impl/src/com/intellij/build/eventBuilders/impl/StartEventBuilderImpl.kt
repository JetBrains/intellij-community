// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.StartEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.impl.StartEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class StartEventBuilderImpl : StartEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  override fun withId(id: Any): StartEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): StartEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): StartEventBuilderImpl =
    apply { this.time = time }

  override fun withMessage(message: @Message String): StartEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): StartEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): StartEventBuilderImpl =
    apply { this.description = description }

  override fun build(): StartEventImpl =
    StartEventImpl(
      id ?: throw IllegalStateException("The StartEvent's 'id' property should be defined"),
      parentId,
      time,
      message ?: throw IllegalStateException("The StartEvent's 'buildDescriptor' property should be defined"),
      hint,
      description
    )
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.MessageEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.BuildEventsNls.Title
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class MessageEventBuilderImpl(
  private val message: @Message String,
  private val kind: MessageEvent.Kind
) : MessageEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var group: @Title String? = null
  private var navigatable: Navigatable? = null

  override fun withId(id: Any?): MessageEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): MessageEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): MessageEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): MessageEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): MessageEventBuilderImpl =
    apply { this.description = description }

  override fun withGroup(group: @Title String?): MessageEventBuilderImpl =
    apply { this.group = group }

  override fun withNavigatable(navigatable: Navigatable?): MessageEventBuilderImpl =
    apply { this.navigatable = navigatable }

  override fun build(): MessageEventImpl =
    MessageEventImpl(id, parentId, time, message, hint, description, kind, group, navigatable)
}

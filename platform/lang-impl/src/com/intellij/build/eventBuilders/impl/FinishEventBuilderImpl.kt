// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.FinishEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.FinishEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FinishEventBuilderImpl(
  private val startId: Any,
  private val message: @Message String,
  private val result: EventResult,
) : FinishEventBuilder {

  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  override fun withParentId(parentId: Any?): FinishEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FinishEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): FinishEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FinishEventBuilderImpl =
    apply { this.description = description }

  override fun build(): FinishEventImpl =
    FinishEventImpl(startId, parentId, time, message, hint, description, result)
}

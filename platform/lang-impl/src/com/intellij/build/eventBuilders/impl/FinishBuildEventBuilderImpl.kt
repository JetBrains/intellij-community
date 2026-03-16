// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.FinishBuildEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.FinishBuildEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FinishBuildEventBuilderImpl(
  private val startBuildId: Any,
  private val message: @Message String,
  private val result: EventResult
) : FinishBuildEventBuilder {

  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  override fun withParentId(parentId: Any?): FinishBuildEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FinishBuildEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): FinishBuildEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FinishBuildEventBuilderImpl =
    apply { this.description = description }

  override fun build(): FinishBuildEventImpl =
    FinishBuildEventImpl(startBuildId, parentId, time, message, hint, description, result)
}

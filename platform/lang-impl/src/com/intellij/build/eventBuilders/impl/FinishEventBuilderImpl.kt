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
class FinishEventBuilderImpl : FinishEventBuilder {

  private var startId: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var result: EventResult? = null

  override fun withStartId(startId: Any): FinishEventBuilderImpl =
    apply { this.startId = startId }

  override fun withParentId(parentId: Any?): FinishEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FinishEventBuilderImpl =
    apply { this.time = time }

  override fun withMessage(message: @Message String): FinishEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): FinishEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FinishEventBuilderImpl =
    apply { this.description = description }

  override fun withResult(result: EventResult): FinishEventBuilderImpl =
    apply { this.result = result }

  override fun build(): FinishEventImpl =
    FinishEventImpl(
      startId ?: throw IllegalStateException("The FinishEvent's 'startId' property should be defined"),
      parentId,
      time,
      message ?: throw IllegalStateException("The FinishEvent's 'message' property should be defined"),
      hint,
      description,
      result ?: throw IllegalStateException("The FinishEvent's 'result' property should be defined")
    )
}

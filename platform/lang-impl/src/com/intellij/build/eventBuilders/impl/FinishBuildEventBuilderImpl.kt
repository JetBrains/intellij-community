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
class FinishBuildEventBuilderImpl : FinishBuildEventBuilder {

  private var startBuildId: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var result: EventResult? = null

  override fun withStartBuildId(startBuildId: Any): FinishBuildEventBuilderImpl =
    apply { this.startBuildId = startBuildId }

  override fun withParentId(parentId: Any?): FinishBuildEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): FinishBuildEventBuilderImpl =
    apply { this.time = time }

  override fun withMessage(message: @Message String): FinishBuildEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): FinishBuildEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): FinishBuildEventBuilderImpl =
    apply { this.description = description }

  override fun withResult(result: EventResult): FinishBuildEventBuilderImpl =
    apply { this.result = result }

  override fun build(): FinishBuildEventImpl =
    FinishBuildEventImpl(
      startBuildId ?: throw IllegalStateException("The FinishBuildEvent's 'startBuildId' property should be defined"),
      parentId,
      time,
      message ?: throw IllegalStateException("The FinishBuildEvent's 'message' property should be defined"),
      hint,
      description,
      result ?: throw IllegalStateException("The FinishBuildEvent's 'result' property should be defined")
    )
}

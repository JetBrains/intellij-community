// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.PresentableBuildEventBuilder
import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.PresentableBuildEvent
import com.intellij.build.events.impl.PresentableBuildEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class PresentableBuildEventBuilderImpl : PresentableBuildEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var presentationData: BuildEventPresentationData? = null

  override fun withId(id: Any?): PresentableBuildEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): PresentableBuildEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): PresentableBuildEventBuilderImpl =
    apply { this.time = time }

  override fun withMessage(message: @Message String): PresentableBuildEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): PresentableBuildEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): PresentableBuildEventBuilderImpl =
    apply { this.description = description }

  override fun withPresentationData(presentationData: BuildEventPresentationData): PresentableBuildEventBuilderImpl =
    apply { this.presentationData = presentationData }

  override fun build(): PresentableBuildEvent {
    return PresentableBuildEventImpl(
      id,
      parentId,
      time,
      message ?: throw IllegalStateException("The PresentableBuildEvent's 'message' property should be defined"),
      hint,
      description,
      presentationData ?: throw IllegalStateException("The PresentableBuildEvent's 'presentationData' property should be defined")
    )
  }
}

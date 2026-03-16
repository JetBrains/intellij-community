// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.OutputBuildEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.impl.OutputBuildEventImpl
import com.intellij.execution.process.ProcessOutputType
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class OutputBuildEventBuilderImpl(
  private val message: @Message String
) : OutputBuildEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var outputType: ProcessOutputType? = null

  override fun withId(id: Any?): OutputBuildEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): OutputBuildEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): OutputBuildEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): OutputBuildEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): OutputBuildEventBuilderImpl =
    apply { this.description = description }

  override fun withOutputType(outputType: ProcessOutputType?): OutputBuildEventBuilderImpl =
    apply { this.outputType = outputType }

  override fun build(): OutputBuildEventImpl =
    OutputBuildEventImpl(id, parentId, time, message, hint, description, outputType)
}
